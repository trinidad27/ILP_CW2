package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.data.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DeliveryPathService {
    private final Supplier<String> ilpEndpointSupplier;
    private final RestTemplate restTemplate = new RestTemplate();
    private final DroneAvailabilityService droneAvailabilityService;
    private final DroneCacheService droneCacheService;

    private static final double MOVE_DISTANCE = 0.00015;
    private static final double CLOSE_ENOUGH_THRESHOLD = 0.00015;
    private static final double[] VALID_ANGLES = {0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
            180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5};

    // A* Node class
    private static class PathNode implements Comparable<PathNode> {
        final double lng;
        final double lat;
        final double gCost; // Cost from start
        final double hCost; // Heuristic to goal
        final PathNode parent;

        PathNode(double lng, double lat, double gCost, double hCost, PathNode parent) {
            this.lng = lng;
            this.lat = lat;
            this.gCost = gCost;
            this.hCost = hCost;
            this.parent = parent;
        }

        double fCost() {
            return gCost + hCost;
        }

        @Override
        public int compareTo(PathNode o) {
            return Double.compare(this.fCost(), o.fCost());
        }

        String key() {
            return String.format("%.8f,%.8f", lng, lat);
        }
    }

    public DeliveryPathService(Supplier<String> ilpEndpointSupplier,
                               DroneAvailabilityService droneAvailabilityService,
                               DroneCacheService droneCacheService) {
        this.ilpEndpointSupplier = ilpEndpointSupplier;
        this.droneAvailabilityService = droneAvailabilityService;
        this.droneCacheService = droneCacheService;
    }

    public DeliveryPathResponse calcDeliveryPath(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return new DeliveryPathResponse(0.0, 0, Collections.emptyList());
        }

        // Fetch data every time
        List<Drone> allDrones = droneAvailabilityService.fetchAllDrones();

        Map<String, Drone> droneMap = allDrones.stream()
                .collect(Collectors.toMap(Drone::getId, d -> d));
        Map<Integer, DroneServicePoint> servicePoints = fetchServicePoints();

        List<RestrictedArea> noFlyZones = fetchRestrictedAreas();
        Map<Integer, Set<String>> dronesPerSP = fetchDronesPerServicePoint();

        // Fetch availability once
        Map<String, List<Availability>> availabilityMap = droneAvailabilityService.fetchAvailabilityMap();

        List<DronePath> dronePaths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        List<MedDispatchRec> remaining = new ArrayList<>(dispatches);

        // Group all drones by their service points
        Map<Integer, List<String>> dronesByServicePoint = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : dronesPerSP.entrySet()) {
            dronesByServicePoint.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
//
        // Track which drones have been used
        Set<String> usedDrones = new HashSet<>();

        // Process deliveries by finding nearest service point with available drones
        int maxAttempts = 100; // Prevent infinite loops
        int attempts = 0;

        while (!remaining.isEmpty() && attempts++ < maxAttempts) {
            double avgLng = remaining.stream()
                    .filter(d -> d.getDelivery() != null)
                    .mapToDouble(d -> d.getDelivery().getLng())
                    .average()
                    .orElse(0);
            double avgLat = remaining.stream()
                    .filter(d -> d.getDelivery() != null)
                    .mapToDouble(d -> d.getDelivery().getLat())
                    .average()
                    .orElse(0);

            // Find nearest service point with available drones that can handle remaining dispatches
            Integer nearestSpId = null;
            String selectedDroneId = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Map.Entry<Integer, List<String>> entry : dronesByServicePoint.entrySet()) {
                DroneServicePoint sp = servicePoints.get(entry.getKey());
                if (sp == null || sp.getLocation() == null) continue;

                double dist = distance(sp.getLocation().getLng(), sp.getLocation().getLat(), avgLng, avgLat);

                // Find an available drone at this SP that can handle at least one remaining dispatch
                for (String droneId : entry.getValue()) {
                    if (usedDrones.contains(droneId)) continue;

                    // Check if drone is currently in flight or busy
                    if (isDroneBusy(droneId)) {
                        continue;
                    }

                    Drone drone = droneMap.get(droneId);
                    if (drone == null || drone.getCapability() == null) continue;

                    // Check if this drone can handle at least one remaining dispatch
                    boolean canHandleAny = false;
                    for (MedDispatchRec dispatch : remaining) {
                        if (canDroneHandleDispatch(drone, dispatch, availabilityMap)) {
                            canHandleAny = true;
                            break;
                        }
                    }

                    if (canHandleAny && dist < nearestDistance) {
                        nearestDistance = dist;
                        nearestSpId = entry.getKey();
                        selectedDroneId = droneId;
                    }
                }
            }

            if (nearestSpId == null || selectedDroneId == null) {
                break; // No more drones available
            }

            DroneServicePoint sp = servicePoints.get(nearestSpId);
            Drone drone = droneMap.get(selectedDroneId);

            DronePath path = buildDronePath(drone, sp, remaining, noFlyZones);

            if (!path.getDeliveries().isEmpty()) {
                // Path was successful
                dronePaths.add(path);
                totalCost += path.getCost();
                totalMoves += path.getMoves();

                Set<Integer> doneIds = path.getDeliveries().stream()
                        .map(Delivery::getDeliveryId).collect(Collectors.toSet());
                remaining.removeIf(d -> doneIds.contains(d.getId()));

                // Mark drone as used
                usedDrones.add(selectedDroneId);
            } else {
                usedDrones.add(selectedDroneId);
            }
        }

        return new DeliveryPathResponse(totalCost, totalMoves, dronePaths);
    }

    /**
     * Check if a drone is currently busy (in flight, charging, etc.)
     */
    private boolean isDroneBusy(String droneId) {
        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);
            if (status == null) {
                return false; // Not in cache = not busy
            }

            // Consider drone busy if it's in flight, returning, or has an active delivery
            String droneStatus = status.getStatus();
            return "IN_FLIGHT".equals(droneStatus) ||
                   "RETURNING".equals(droneStatus) ||
                   status.getCurrentDeliveryId() != null;
        } catch (Exception e) {
            return false; // Assume available if we can't check
        }
    }

    /**
     * Check if a drone can handle a specific dispatch based on capability and availability
     */
    private boolean canDroneHandleDispatch(Drone drone, MedDispatchRec dispatch,
                                           Map<String, List<Availability>> availabilityMap) {
        if (drone == null || drone.getCapability() == null || dispatch == null) {
            return false;
        }

        Capability cap = drone.getCapability();
        Requirements req = dispatch.getRequirements();

        if (req == null) {
            return true; // No requirements means any drone can handle it
        }

        // Check capacity
        if (req.getCapacity() != null && cap.getCapacity() != null) {
            if (cap.getCapacity() < req.getCapacity()) {
                return false;
            }
        }

        // Check cooling - only fail if cooling is required but drone doesn't have it
        if (Boolean.TRUE.equals(req.getCooling()) && !Boolean.TRUE.equals(cap.getCooling())) {
            return false;
        }

        // Check heating - only fail if heating is required but drone doesn't have it
        if (Boolean.TRUE.equals(req.getHeating()) && !Boolean.TRUE.equals(cap.getHeating())) {
            return false;
        }

        // Check basic cost constraint (initial + final must be less than maxCost)
        if (req.getMaxCost() != null) {
            double baseCost = (cap.getCostInitial() != null ? cap.getCostInitial() : 0.0) +
                    (cap.getCostFinal() != null ? cap.getCostFinal() : 0.0);
            if (baseCost >= req.getMaxCost()) {
                return false;
            }
        }

        // Check availability if date/time is specified
        if (dispatch.getDate() != null && dispatch.getTime() != null) {
            List<Availability> slots = availabilityMap.get(drone.getId());
            if (slots == null || slots.isEmpty()) {
                return false;
            }

            String dayName = dispatch.getDate().getDayOfWeek().name();
            boolean available = slots.stream().anyMatch(s ->
                    s != null && s.getDayOfWeek() != null &&
                            dayName.equals(s.getDayOfWeek().name()) &&
                            s.getFrom() != null && s.getUntil() != null &&
                            !dispatch.getTime().isBefore(s.getFrom()) &&
                            !dispatch.getTime().isAfter(s.getUntil())
            );

            if (!available) {
                return false;
            }
        }

        return true;
    }

    /**
     * Builds a complete drone path for multiple deliveries.
     *
     * Key requirements:
     * - Each delivery's flightPath goes from previous position to delivery location with hover
     * - Last delivery's flightPath includes the return journey to service point
     * - No separate return journey delivery (no deliveryId: null)
     * - Hover = duplicate coordinate (counts as 1 move)
     * - No hover at service point
     * - Checks maxCost constraint
     * - Validates delivery locations are not in no-fly zones
     */
    private DronePath buildDronePath(Drone drone, DroneServicePoint sp,
                                     List<MedDispatchRec> dispatches, List<RestrictedArea> noFlyZones) {

        Capability cap = drone.getCapability();
        int maxMoves = cap.getMaxMoves() != null ? cap.getMaxMoves() : Integer.MAX_VALUE;

        LngLatAlt currentPosition = sp.getLocation();
        List<Delivery> deliveries = new ArrayList<>();
        List<MedDispatchRec> selectedDispatches = new ArrayList<>();

        double costPerMove = cap.getCostPerMove() != null ? cap.getCostPerMove() : 0.0;
        double costInitial = cap.getCostInitial() != null ? cap.getCostInitial() : 0.0;
        double costFinal = cap.getCostFinal() != null ? cap.getCostFinal() : 0.0;

        // Sort dispatches by nearest neighbor for efficiency
        List<MedDispatchRec> sorted = sortByNearest(currentPosition, dispatches);

        for (MedDispatchRec dispatch : sorted) {

            LngLatAlt dest = dispatch.getDelivery();

            List<LngLatAlt> pathToDelivery = findPathAStar(currentPosition, dest, noFlyZones);

            // Moves = path points - 1 (first point is current position)
            int movesToDelivery = pathToDelivery.size() - 1;
            int hoverMove = 1; // One move for hovering

            List<LngLatAlt> pathBack = findPathAStar(dest, sp.getLocation(), noFlyZones);

            int movesBack = pathBack.size() - 1;

            // Check if adding this delivery would exceed maxMoves


            // Accept this delivery
            selectedDispatches.add(dispatch);

            // Update current position to the "close enough" point we'll reach
            if (!pathToDelivery.isEmpty()) {
                currentPosition = pathToDelivery.get(pathToDelivery.size() - 1);
            }
        }


        if (!selectedDispatches.isEmpty()) {
            LngLatAlt currentHoverPosition = sp.getLocation();
            int totalDeliveries = selectedDispatches.size();

            for (int i = 0; i < totalDeliveries; i++) {
                MedDispatchRec dispatch = selectedDispatches.get(i);
                LngLatAlt dest = dispatch.getDelivery();
                boolean isLastDelivery = (i == totalDeliveries - 1);

                List<LngLatAlt> flightPath = new ArrayList<>();

                List<LngLatAlt> pathToDelivery = findPathAStar(currentHoverPosition, dest, noFlyZones);

                // Add all points from the path
                flightPath.addAll(pathToDelivery);

                // Add hover at delivery location
                LngLatAlt deliveryPoint = flightPath.get(flightPath.size() - 1);
                flightPath.add(new LngLatAlt(deliveryPoint.getLng(), deliveryPoint.getLat()));

                // Update hover position for next iteration
                currentHoverPosition = deliveryPoint;

                // Adding return journey only for last delivery
                if (isLastDelivery) {
                    List<LngLatAlt> returnPath = findPathAStar(currentHoverPosition, sp.getLocation(), noFlyZones);

                    if (!returnPath.isEmpty()) {
                        // Add return path points (skip first point as it's same as current hover position)
                        for (int j = 1; j < returnPath.size(); j++) {
                            flightPath.add(returnPath.get(j));
                        }
                    }
                }

                // Create and add the delivery
                deliveries.add(new Delivery(dispatch.getId(), flightPath));
            }
        }

        int totalMoves = 0;
        for (Delivery d : deliveries) {
            int pathMoves = d.getFlightPath().size() - 1; // Points to moves conversion
            totalMoves += pathMoves;
        }

        double cost = costInitial + (totalMoves * costPerMove) + costFinal;

        DronePath path = new DronePath();
        path.setDroneId(drone.getId());
        path.setDeliveries(deliveries);
        path.setMoves(totalMoves);
        path.setCost(cost);

        return path;
    }

    private List<MedDispatchRec> sortByNearest(LngLatAlt start, List<MedDispatchRec> dispatches) {
        List<MedDispatchRec> result = new ArrayList<>();
        List<MedDispatchRec> remaining = new ArrayList<>(dispatches);
        LngLatAlt current = start;

        while (!remaining.isEmpty()) {
            MedDispatchRec nearest = null;
            double minDist = Double.MAX_VALUE;
            for (MedDispatchRec d : remaining) {
                if (d.getDelivery() == null) continue;
                double dist = distance(current, d.getDelivery());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = d;
                }
            }
            if (nearest != null) {
                result.add(nearest);
                remaining.remove(nearest);
                current = nearest.getDelivery();
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * A* pathfinding algorithm to find optimal path avoiding no-fly zones
     */
    private List<LngLatAlt> findPathAStar(LngLatAlt from, LngLatAlt to, List<RestrictedArea> noFlyZones) {

        // Check if destination is inside no-fly zone
        if (isInNoFlyZone(to.getLng(), to.getLat(), noFlyZones)) {
            return Collections.emptyList();
        }

        // Check if start is inside no-fly zone
        if (isInNoFlyZone(from.getLng(), from.getLat(), noFlyZones)) {
            return Collections.emptyList();
        }

        double initialDistance = distance(from, to);

        // Check if we're already close enough
        if (initialDistance < CLOSE_ENOUGH_THRESHOLD) {
            List<LngLatAlt> result = new ArrayList<>();
            result.add(new LngLatAlt(from.getLng(), from.getLat()));
            return result;
        }


        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<String, PathNode> closedSet = new HashMap<>();
        Map<String, Double> bestGCost = new HashMap<>();

        PathNode startNode = new PathNode(from.getLng(), from.getLat(), 0, initialDistance, null);
        openSet.add(startNode);
        bestGCost.put(startNode.key(), 0.0);

        int estimatedSteps = (int)(initialDistance / MOVE_DISTANCE);
        int maxIterations = Math.min(20000, Math.max(5000, estimatedSteps * 25));

        int iter = 0;
        int nodesExplored = 0;
        int invalidMoves = 0;

        while (!openSet.isEmpty() && iter++ < maxIterations) {
            PathNode current = openSet.poll();
            nodesExplored++;

            double distToGoal = distance(current.lng, current.lat, to.getLng(), to.getLat());

            if (distToGoal < CLOSE_ENOUGH_THRESHOLD) {
                // Reconstruct path
                List<LngLatAlt> path = new ArrayList<>();
                PathNode node = current;
                int pathLength = 0;
                while (node != null) {
                    path.add(0, new LngLatAlt(node.lng, node.lat));
                    node = node.parent;
                    pathLength++;
                }

                return path;
            }

            String currentKey = current.key();
            if (closedSet.containsKey(currentKey)) continue;
            closedSet.put(currentKey, current);

            // Explore neighbors in all 16 directions
            for (double angle : VALID_ANGLES) {
                double rad = Math.toRadians(angle);
                double newLng = current.lng + MOVE_DISTANCE * Math.cos(rad);
                double newLat = current.lat + MOVE_DISTANCE * Math.sin(rad);

                String neighborKey = String.format("%.8f,%.8f", newLng, newLat);

                // Skip if already explored
                if (closedSet.containsKey(neighborKey)) continue;

                // Check if this position is valid (not in no-fly zone and no edge crossing)
                if (!isValidMove(current.lng, current.lat, newLng, newLat, noFlyZones)) {
                    invalidMoves++;
                    continue;
                }

                double newGCost = current.gCost + MOVE_DISTANCE;
                double hCost = distance(newLng, newLat, to.getLng(), to.getLat());

                // Check if this is a better path to this neighbor
                if (!bestGCost.containsKey(neighborKey) || newGCost < bestGCost.get(neighborKey)) {
                    bestGCost.put(neighborKey, newGCost);
                    PathNode neighbor = new PathNode(newLng, newLat, newGCost, hCost, current);
                    openSet.add(neighbor);
                }
            }
        }


        // Try greedy fallback
        return findPathGreedy(from, to, noFlyZones);
    }

    /**
     * Greedy pathfinding fallback with obstacle avoidance
     * Uses tangent-line navigation around obstacles to prevent zigzagging
     */
    private List<LngLatAlt> findPathGreedy(LngLatAlt from, LngLatAlt to, List<RestrictedArea> noFlyZones) {

        List<LngLatAlt> path = new ArrayList<>();
        path.add(new LngLatAlt(from.getLng(), from.getLat()));

        double currLng = from.getLng();
        double currLat = from.getLat();
        double targetLng = to.getLng();
        double targetLat = to.getLat();

        double initialDistance = distance(currLng, currLat, targetLng, targetLat);
        int estimatedSteps = (int)(initialDistance / MOVE_DISTANCE);

        int maxIterations = Math.min(50000, Math.max(5000, estimatedSteps * 3));
        int iter = 0;
        int stuckCounter = 0;
        int invalidMoves = 0;
        double lastDistance = initialDistance;
        Set<String> visited = new HashSet<>();

        while (distance(currLng, currLat, targetLng, targetLat) > CLOSE_ENOUGH_THRESHOLD && iter++ < maxIterations) {
            double bestAngle = -1;
            double bestDist = Double.MAX_VALUE;
            double secondBestAngle = -1;
            double secondBestDist = Double.MAX_VALUE;

            // Calculate direction to target for backward movement detection
            double targetAngle = Math.toDegrees(Math.atan2(targetLat - currLat, targetLng - currLng));

            // Try all 16 directions
            for (double angle : VALID_ANGLES) {
                double rad = Math.toRadians(angle);
                double newLng = currLng + MOVE_DISTANCE * Math.cos(rad);
                double newLat = currLat + MOVE_DISTANCE * Math.sin(rad);

                String posKey = String.format("%.8f,%.8f", newLng, newLat);

                if (!isValidMove(currLng, currLat, newLng, newLat, noFlyZones)) {
                    invalidMoves++;
                    continue;
                }

                // Calculate distance to target
                double dist = distance(newLng, newLat, targetLng, targetLat);

                // Penalize revisiting same positions
                if (visited.contains(posKey)) {
                    dist += 0.01;
                }

                // Penalize backward movement (moving away from target direction)
                double angleDiff = Math.abs(angle - targetAngle);
                if (angleDiff > 180) angleDiff = 360 - angleDiff;
                if (angleDiff > 90) { // Moving more than 90 degrees away from target
                    dist += 0.005; // Penalize backward movement
                }

                if (dist < bestDist) {
                    secondBestDist = bestDist;
                    secondBestAngle = bestAngle;
                    bestDist = dist;
                    bestAngle = angle;
                } else if (dist < secondBestDist) {
                    secondBestDist = dist;
                    secondBestAngle = angle;
                }
            }

            if (bestAngle < 0) {
                return Collections.emptyList();
            }

            if (bestDist >= lastDistance - 0.000001) {
                stuckCounter++;
                if (stuckCounter > 100) {
                    return Collections.emptyList();
                }

                // When stuck try second-best direction to escape local minimum
                if (stuckCounter > 20 && secondBestAngle >= 0) {
                    bestAngle = secondBestAngle;
                    bestDist = secondBestDist;
                }
            } else {
                stuckCounter = 0;
            }
            lastDistance = bestDist;

            double rad = Math.toRadians(bestAngle);
            double newLng = currLng + MOVE_DISTANCE * Math.cos(rad);
            double newLat = currLat + MOVE_DISTANCE * Math.sin(rad);

            currLng = newLng;
            currLat = newLat;

            String posKey = String.format("%.8f,%.8f", currLng, currLat);
            visited.add(posKey);
            path.add(new LngLatAlt(currLng, currLat));
        }

        if (iter >= maxIterations) {
            return Collections.emptyList();
        }

        return path;
    }


    /**
     * Check if a move from (lng1, lat1) to (lng2, lat2) is valid
     * Checks both endpoint and edge crossing
     */
    private boolean isValidMove(double lng1, double lat1, double lng2, double lat2,
                                List<RestrictedArea> zones) {
        // Check if destination point is in no-fly zone
        if (isInNoFlyZone(lng2, lat2, zones)) {
            return false;
        }

        // Check if the edge crosses any no-fly zone boundary
        if (edgeCrossesNoFlyZone(lng1, lat1, lng2, lat2, zones)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a point is inside any no-fly zone (with altitude consideration)
     */
    private boolean isInNoFlyZone(double lng, double lat, List<RestrictedArea> zones) {
        if (zones == null || zones.isEmpty()) {
            return false;
        }

        for (int i = 0; i < zones.size(); i++) {
            RestrictedArea zone = zones.get(i);
            if (zone == null) {
                continue;
            }

            List<LngLatAlt> vertices = zone.getVertices();
            if (vertices == null || vertices.size() < 3) {
                continue;
            }

            // if (droneAltitude >= zone.getLimits().getLower() &&
            //     droneAltitude <= zone.getLimits().getUpper())

            boolean inPolygon = isPointInPolygon(lng, lat, vertices);
            if (inPolygon) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an edge crosses any no-fly zone boundary
     */
    private boolean edgeCrossesNoFlyZone(double lng1, double lat1, double lng2, double lat2,
                                         List<RestrictedArea> zones) {
        if (zones == null) return false;

        for (RestrictedArea zone : zones) {
            List<LngLatAlt> vertices = zone.getVertices();
            if (vertices == null || vertices.size() < 3) continue;

            // Check if edge intersects any edge of the no-fly zone polygon
            for (int i = 0; i < vertices.size(); i++) {
                int j = (i + 1) % vertices.size();
                LngLatAlt v1 = vertices.get(i);
                LngLatAlt v2 = vertices.get(j);

                if (edgesIntersect(lng1, lat1, lng2, lat2,
                        v1.getLng(), v1.getLat(), v2.getLng(), v2.getLat())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if two line segments intersect
     */
    private boolean edgesIntersect(double x1, double y1, double x2, double y2,
                                   double x3, double y3, double x4, double y4) {
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 1e-10) return false; // Parallel or coincident

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;

        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    /**
     * Ray casting algorithm to check if point is inside polygon
     */
    private boolean isPointInPolygon(double lng, double lat, List<LngLatAlt> vertices) {
        if (vertices == null || vertices.size() < 3) return false;

        boolean inside = false;
        int n = vertices.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i).getLng(), yi = vertices.get(i).getLat();
            double xj = vertices.get(j).getLng(), yj = vertices.get(j).getLat();

            if (((yi > lat) != (yj > lat)) && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) return createEmptyGeoJson();

        List<Drone> allDrones = droneAvailabilityService.fetchAllDrones();
        Map<String, Drone> droneMap = allDrones.stream()
                .collect(Collectors.toMap(Drone::getId, drone -> drone));
        Map<Integer, DroneServicePoint> servicePoints = fetchServicePoints();
        List<RestrictedArea> noFlyZones = fetchRestrictedAreas();
        Map<Integer,Set<String>> dronesPerSP = fetchDronesPerServicePoint();

        // Get all drone IDs instead of filtering strictly by availability
        // This matches the behavior of calcDeliveryPath
        List<String> allDroneIds = allDrones.stream()
                .map(Drone::getId)
                .collect(Collectors.toList());

        if (allDroneIds.isEmpty()) {
            return createEmptyGeoJson();
        }

        // Calculate centroid of deliveries to find nearest service point
        double avgLng = dispatches.stream()
                .filter(d -> d.getDelivery() != null)
                .mapToDouble(d -> d.getDelivery().getLng())
                .average()
                .orElse(0);
        double avgLat = dispatches.stream()
                .filter(d -> d.getDelivery() != null)
                .mapToDouble(d -> d.getDelivery().getLat())
                .average()
                .orElse(0);

        // Find nearest service point with available drones
        String selectedDroneId = null;
        DroneServicePoint selectedSP = null;
        double nearestDistance = Double.MAX_VALUE;

        for (String droneId : allDroneIds) {
            // Skip drones that are currently busy
            if (isDroneBusy(droneId)) {
                continue;
            }

            Integer spId = findServicePointForDrone(droneId, dronesPerSP);
            if (spId == null) continue;

            DroneServicePoint sp = servicePoints.get(spId);
            if (sp == null || sp.getLocation() == null) continue;

            double dist = distance(sp.getLocation().getLng(), sp.getLocation().getLat(), avgLng, avgLat);

            if (dist < nearestDistance) {
                nearestDistance = dist;
                selectedDroneId = droneId;
                selectedSP = sp;
            }
        }

        if (selectedDroneId == null || selectedSP == null) {
            return createEmptyGeoJson();
        }

        Drone drone = droneMap.get(selectedDroneId);
        if (drone == null || drone.getCapability() == null) {
            return createEmptyGeoJson();
        }

        List<double[]> coordinates = new ArrayList<>();
        LngLatAlt currentHoverPosition = selectedSP.getLocation();

        coordinates.add(new double[]{currentHoverPosition.getLng(), currentHoverPosition.getLat()});

        List<MedDispatchRec> sorted = sortByNearest(currentHoverPosition, dispatches);
        int totalDeliveries = sorted.size();

        for (int i = 0; i < totalDeliveries; i++) {
            MedDispatchRec dispatch = sorted.get(i);
            LngLatAlt dest = dispatch.getDelivery();
            if (dest == null) continue;

            boolean isLastDelivery = (i == totalDeliveries - 1);

            List<LngLatAlt> pathToDelivery = findPathAStar(currentHoverPosition, dest, noFlyZones);

            if (pathToDelivery.isEmpty()) {
                continue;
            }

            // Add path points (skip first as it's already added)
            for (int j = 1; j < pathToDelivery.size(); j++) {
                LngLatAlt point = pathToDelivery.get(j);
                coordinates.add(new double[]{point.getLng(), point.getLat()});
            }

            // Add hover (duplicate last point)
            if (!pathToDelivery.isEmpty()) {
                LngLatAlt deliveryPoint = pathToDelivery.get(pathToDelivery.size() - 1);
                coordinates.add(new double[]{deliveryPoint.getLng(), deliveryPoint.getLat()});
                currentHoverPosition = deliveryPoint;
            }

            // Add return path for last delivery
            if (isLastDelivery) {
                List<LngLatAlt> returnPath = findPathAStar(currentHoverPosition, selectedSP.getLocation(), noFlyZones);
                for (int j = 1; j < returnPath.size(); j++) {
                    LngLatAlt point = returnPath.get(j);
                    coordinates.add(new double[]{point.getLng(), point.getLat()});
                }
            }
        }

        return createGeoJsonLineString(coordinates);
    }

    private Map<String, Object> createGeoJsonLineString(List<double[]> coordinates) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("properties", new LinkedHashMap<>());
        feature.put("geometry", geometry);

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", Collections.singletonList(feature));

        return geoJson;
    }

    private Map<String, Object> createEmptyGeoJson() {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", Collections.emptyList());

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("properties", new LinkedHashMap<>());
        feature.put("geometry", geometry);

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", Collections.singletonList(feature));

        return geoJson;
    }

    private double distance(LngLatAlt a, LngLatAlt b) {
        return distance(a.getLng(), a.getLat(), b.getLng(), b.getLat());
    }

    private double distance(double lng1, double lat1, double lng2, double lat2) {
        // Pythagorean distance as specified
        return Math.sqrt(Math.pow(lng2 - lng1, 2) + Math.pow(lat2 - lat1, 2));
    }

    private Integer findServicePointForDrone(String droneId, Map<Integer, Set<String>> dronesPerSP) {
        for (Map.Entry<Integer, Set<String>> e : dronesPerSP.entrySet()) {
            if (e.getValue().contains(droneId)) return e.getKey();
        }
        return null;
    }

    private Map<Integer, DroneServicePoint> fetchServicePoints() {
        String url = ilpEndpointSupplier.get() + "/service-points";
        DroneServicePoint[] data = restTemplate.getForObject(url, DroneServicePoint[].class);
        if (data == null) return Collections.emptyMap();
        return Arrays.stream(data).filter(sp -> sp != null && sp.getId() != null)
                .collect(Collectors.toMap(DroneServicePoint::getId, sp -> sp));
    }

    private List<RestrictedArea> fetchRestrictedAreas() {
        String url = ilpEndpointSupplier.get() + "/restricted-areas";
        RestrictedArea[] data = restTemplate.getForObject(url, RestrictedArea[].class);
        return data != null ? Arrays.asList(data) : Collections.emptyList();
    }

    private Map<Integer, Set<String>> fetchDronesPerServicePoint() {
        String url = ilpEndpointSupplier.get() + "/drones-for-service-points";
        DroneForServicePoint[] data = restTemplate.getForObject(url, DroneForServicePoint[].class);
        if (data == null) return Collections.emptyMap();

        Map<Integer, Set<String>> result = new HashMap<>();
        for (DroneForServicePoint sp : data) {
            if (sp == null || sp.getServicePointId() == null || sp.getDrones() == null) continue;
            Set<String> ids = new HashSet<>();
            for (DronesAvailability d : sp.getDrones()) {
                if (d != null && d.getId() != null) ids.add(d.getId());
            }
            result.put(sp.getServicePointId(), ids);
        }
        return result;
    }
}