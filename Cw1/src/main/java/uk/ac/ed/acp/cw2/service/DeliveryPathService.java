package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.data.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
public class DeliveryPathService {
    private final Supplier<String> ilpEndpointSupplier;
    private final RestTemplate restTemplate = new RestTemplate();
    private final DroneAvailabilityService droneAvailabilityService;

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
                               DroneAvailabilityService droneAvailabilityService) {
        this.ilpEndpointSupplier = ilpEndpointSupplier;
        this.droneAvailabilityService = droneAvailabilityService;
    }

    public DeliveryPathResponse calcDeliveryPath(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return new DeliveryPathResponse(0.0, 0, Collections.emptyList());
        }

        // Fetch data fresh every time
        List<Drone> allDrones = droneAvailabilityService.fetchAllDrones();
        System.out.println("All drones: " + allDrones.size());

        Map<String, Drone> droneMap = allDrones.stream()
                .collect(Collectors.toMap(Drone::getId, d -> d));
        Map<Integer, DroneServicePoint> servicePoints = fetchServicePoints();
        System.out.println("Service points: " + servicePoints.keySet());

        List<RestrictedArea> noFlyZones = fetchRestrictedAreas();
        Map<Integer, Set<String>> dronesPerSP = fetchDronesPerServicePoint();
        System.out.println("Drones per SP: " + dronesPerSP);

        // Find available drones
        List<String> availableDroneIds = droneAvailabilityService.findAvailableDrones(dispatches);
        System.out.println("Available drones: " + availableDroneIds);

        if (availableDroneIds.isEmpty()) {
            return new DeliveryPathResponse(0.0, 0, Collections.emptyList());
        }

        List<DronePath> dronePaths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        List<MedDispatchRec> remaining = new ArrayList<>(dispatches);

        // Group available drones by their service points
        Map<Integer, List<String>> dronesByServicePoint = new HashMap<>();
        for (String droneId : availableDroneIds) {
            Integer spId = findServicePointForDrone(droneId, dronesPerSP);
            if (spId != null) {
                dronesByServicePoint.computeIfAbsent(spId, k -> new ArrayList<>()).add(droneId);
            }
        }

        System.out.println("Drones grouped by service point: " + dronesByServicePoint);

        // Process deliveries by finding nearest service point with available drones
        while (!remaining.isEmpty()) {
            // Calculate centroid of remaining deliveries to find nearest SP
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

            System.out.println("Remaining deliveries centroid: (" + avgLng + ", " + avgLat + ")");

            // Find nearest service point with available drones
            Integer nearestSpId = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Map.Entry<Integer, List<String>> entry : dronesByServicePoint.entrySet()) {
                if (entry.getValue().isEmpty()) continue; // No more drones at this SP

                DroneServicePoint sp = servicePoints.get(entry.getKey());
                if (sp == null || sp.getLocation() == null) continue;

                double dist = distance(sp.getLocation().getLng(), sp.getLocation().getLat(), avgLng, avgLat);
                if (dist < nearestDistance) {
                    nearestDistance = dist;
                    nearestSpId = entry.getKey();
                }
            }

            if (nearestSpId == null) {
                System.out.println("No more service points with available drones");
                break; // No more drones available anywhere
            }

            DroneServicePoint sp = servicePoints.get(nearestSpId);
            List<String> availableDronesAtSP = dronesByServicePoint.get(nearestSpId);
            String droneId = availableDronesAtSP.get(0); // Pick first available drone
            Drone drone = droneMap.get(droneId);

            System.out.println("Selected SP: " + sp.getName() + " (id=" + nearestSpId + ", distance=" + nearestDistance + ")");
            System.out.println("Selected drone: " + droneId);

            if (drone == null || drone.getCapability() == null) {
                availableDronesAtSP.remove(0);
                continue;
            }

            System.out.println("  Building path...");
            DronePath path = buildDronePath(drone, sp, remaining, noFlyZones);
            System.out.println("  Path deliveries: " + path.getDeliveries().size());

            if (!path.getDeliveries().isEmpty()) {
                dronePaths.add(path);
                totalCost += path.getCost();
                totalMoves += path.getMoves();

                Set<Integer> doneIds = path.getDeliveries().stream()
                        .map(Delivery::getDeliveryId).collect(Collectors.toSet());
                remaining.removeIf(d -> doneIds.contains(d.getId()));
            }

            // Remove this drone from available pool
            availableDronesAtSP.remove(0);
        }

        return new DeliveryPathResponse(totalCost, totalMoves, dronePaths);
    }

    /**
     * Builds a complete drone path for multiple deliveries.
     *
     * Key requirements:
     * - Each delivery's flightPath goes from previous position to delivery location with hover
     * - LAST delivery's flightPath includes the return journey to service point
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

        double cost = cap.getCostInitial() != null ? cap.getCostInitial() : 0.0;
        double costPerMove = cap.getCostPerMove() != null ? cap.getCostPerMove() : 0.0;

        // Sort dispatches by nearest neighbor for efficiency
        List<MedDispatchRec> sorted = sortByNearest(currentPosition, dispatches);

        // ========================================
        // PHASE 1: PRE-SELECT deliveries that fit within maxMoves AND maxCost
        // ========================================
        int estimatedMoves = 0;

        for (MedDispatchRec dispatch : sorted) {
            LngLatAlt dest = dispatch.getDelivery();
            if (dest == null) continue;

            // ✅ FIX 1: Check if delivery location is in no-fly zone
            if (isInNoFlyZone(dest.getLng(), dest.getLat(), noFlyZones)) {
                System.out.println("WARNING: Delivery " + dispatch.getId() +
                        " is inside no-fly zone at (" + dest.getLng() + ", " +
                        dest.getLat() + ") - SKIPPING");
                continue;
            }

            // Calculate path to this delivery
            List<LngLatAlt> pathToDelivery = findPathAStar(currentPosition, dest, noFlyZones);
            if (pathToDelivery.isEmpty()) {
                System.out.println("WARNING: No path found to delivery " + dispatch.getId() + " - SKIPPING");
                continue;
            }

            // Moves = path points - 1 (first point is current position)
            int movesToDelivery = pathToDelivery.size() - 1;
            int hoverMove = 1; // One move for hovering

            // Calculate return path from THIS delivery to service point
            List<LngLatAlt> pathBack = findPathAStar(dest, sp.getLocation(), noFlyZones);
            if (pathBack.isEmpty()) {
                System.out.println("WARNING: No return path from delivery " + dispatch.getId() + " - SKIPPING");
                continue;
            }

            int movesBack = pathBack.size() - 1;

            // Check if adding this delivery would exceed maxMoves
            if (estimatedMoves + movesToDelivery + hoverMove + movesBack > maxMoves) {
                System.out.println("Skipping delivery " + dispatch.getId() +
                        " - would exceed maxMoves (" + maxMoves + ")");
                continue;
            }

            // ✅ FIX 2: Check maxCost constraint
            if (dispatch.getRequirements() != null &&
                    dispatch.getRequirements().getMaxCost() != null) {

                double estimatedTotalMoves = estimatedMoves + movesToDelivery + hoverMove + movesBack;
                double estimatedCost = (cap.getCostInitial() != null ? cap.getCostInitial() : 0.0) +
                        (estimatedTotalMoves * costPerMove) +
                        (cap.getCostFinal() != null ? cap.getCostFinal() : 0.0);

                if (estimatedCost > dispatch.getRequirements().getMaxCost()) {
                    System.out.println("Skipping delivery " + dispatch.getId() +
                            " - estimated cost " + String.format("%.2f", estimatedCost) +
                            " exceeds maxCost " + dispatch.getRequirements().getMaxCost());
                    continue;
                }
            }

            // Accept this delivery
            selectedDispatches.add(dispatch);
            estimatedMoves += movesToDelivery + hoverMove;

            // Update current position to the "close enough" point we'll reach
            // (This is the hover position, not the exact delivery coordinates)
            if (!pathToDelivery.isEmpty()) {
                currentPosition = pathToDelivery.get(pathToDelivery.size() - 1);
            }
        }

        // ========================================
        // PHASE 2: BUILD actual flight paths with correct structure
        // ========================================
        if (!selectedDispatches.isEmpty()) {
            LngLatAlt currentHoverPosition = sp.getLocation();
            int totalDeliveries = selectedDispatches.size();

            for (int i = 0; i < totalDeliveries; i++) {
                MedDispatchRec dispatch = selectedDispatches.get(i);
                LngLatAlt dest = dispatch.getDelivery();
                boolean isLastDelivery = (i == totalDeliveries - 1);

                List<LngLatAlt> flightPath = new ArrayList<>();

                // Step 1: Path from current hover position to this delivery
                List<LngLatAlt> pathToDelivery = findPathAStar(currentHoverPosition, dest, noFlyZones);

                if (pathToDelivery.isEmpty()) {
                    System.out.println("Warning: Path to delivery " + dispatch.getId() + " is empty during build phase");
                    continue;
                }

                // Add all points from the path
                flightPath.addAll(pathToDelivery);

                // Step 2: Add hover at delivery location (duplicate last point)
                LngLatAlt deliveryPoint = flightPath.get(flightPath.size() - 1);
                flightPath.add(new LngLatAlt(deliveryPoint.getLng(), deliveryPoint.getLat()));

                // Update hover position for next iteration
                currentHoverPosition = deliveryPoint;

                // Step 3: If this is the LAST delivery, add return journey to service point
                if (isLastDelivery) {
                    List<LngLatAlt> returnPath = findPathAStar(currentHoverPosition, sp.getLocation(), noFlyZones);

                    if (!returnPath.isEmpty()) {
                        // Add return path points (skip first point as it's same as current hover position)
                        for (int j = 1; j < returnPath.size(); j++) {
                            flightPath.add(returnPath.get(j));
                        }
                    } else {
                        System.out.println("Warning: Return path from delivery " + dispatch.getId() + " is empty");
                    }
                }

                // Create and add the delivery
                deliveries.add(new Delivery(dispatch.getId(), flightPath));
            }
        }

        // ========================================
        // PHASE 3: Calculate total moves and cost
        // ========================================
        int totalMoves = 0;
        for (Delivery d : deliveries) {
            int pathMoves = d.getFlightPath().size() - 1; // Points to moves conversion
            totalMoves += pathMoves;
        }

        cost += totalMoves * costPerMove;
        cost += cap.getCostFinal() != null ? cap.getCostFinal() : 0.0;

        // ========================================
        // PHASE 4: Create and return DronePath
        // ========================================
        DronePath path = new DronePath();
        path.setDroneId(drone.getId());
        path.setDeliveries(deliveries);
        path.setMoves(totalMoves);
        path.setCost(cost);

        System.out.println("Built drone path: " + drone.getId() +
                " with " + deliveries.size() + " deliveries, " +
                totalMoves + " moves, cost: " + String.format("%.2f", cost));

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
        System.out.println("findPathAStar called:");
        System.out.println("  From: " + from.getLng() + ", " + from.getLat());
        System.out.println("  To: " + to.getLng() + ", " + to.getLat());

        // ✅ FIX 3: Check if destination is inside no-fly zone
        if (isInNoFlyZone(to.getLng(), to.getLat(), noFlyZones)) {
            System.out.println("  ERROR: Destination is inside no-fly zone!");
            return Collections.emptyList();
        }

        // ✅ FIX 4: Check if start is inside no-fly zone
        if (isInNoFlyZone(from.getLng(), from.getLat(), noFlyZones)) {
            System.out.println("  ERROR: Start position is inside no-fly zone!");
            return Collections.emptyList();
        }

        double initialDistance = distance(from, to);
        System.out.println("  Initial distance: " + initialDistance);
        System.out.println("  Estimated steps: " + (int)(initialDistance / MOVE_DISTANCE));

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

        // ✅ FIX 5: Increase max iterations and scale with complexity
        int estimatedSteps = (int)(initialDistance / MOVE_DISTANCE);
        int complexityFactor = (noFlyZones != null && !noFlyZones.isEmpty()) ? 100 : 50;
        int maxIterations = Math.max(100000, estimatedSteps * complexityFactor);
        System.out.println("  A* max iterations: " + maxIterations +
                " (estimated steps: " + estimatedSteps +
                ", complexity factor: " + complexityFactor + ")");

        int iter = 0;

        // ✅ FIX 6: Add diagnostic counters
        int nodesExplored = 0;
        int nodesGenerated = 0;
        double bestDistanceAchieved = initialDistance;

        while (!openSet.isEmpty() && iter++ < maxIterations) {
            PathNode current = openSet.poll();
            nodesExplored++;

            // Check if we reached the goal (or close enough)
            double distToGoal = distance(current.lng, current.lat, to.getLng(), to.getLat());

            // ✅ FIX 7: Track best distance
            if (distToGoal < bestDistanceAchieved) {
                bestDistanceAchieved = distToGoal;
            }

            if (distToGoal < CLOSE_ENOUGH_THRESHOLD) {
                // ✅ FIX 8: Success logging
                System.out.println("  ✓ A* SUCCESS in " + iter + " iterations");
                System.out.println("    Explored: " + nodesExplored + ", Generated: " + nodesGenerated);

                // Reconstruct path
                List<LngLatAlt> path = new ArrayList<>();
                PathNode node = current;
                while (node != null) {
                    path.add(0, new LngLatAlt(node.lng, node.lat));
                    node = node.parent;
                }
                System.out.println("  Path found with " + path.size() + " points");
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
                    continue;
                }

                // ✅ FIX 9: Count generated nodes
                nodesGenerated++;

                double newGCost = current.gCost + MOVE_DISTANCE;
                double hCost = distance(newLng, newLat, to.getLng(), to.getLat());

                // Check if this is a better path to this neighbor
                if (!bestGCost.containsKey(neighborKey) || newGCost < bestGCost.get(neighborKey)) {
                    bestGCost.put(neighborKey, newGCost);
                    PathNode neighbor = new PathNode(newLng, newLat, newGCost, hCost, current);
                    openSet.add(neighbor);
                }
            }

            // Progress logging every 10000 iterations
            if (iter % 10000 == 0) {
                System.out.println("  A* iter " + iter + ": openSet=" + openSet.size() +
                        ", closedSet=" + closedSet.size() +
                        ", best dist=" + String.format("%.6f", bestDistanceAchieved) +
                        ", generated=" + nodesGenerated);
            }
        }

        // ✅ FIX 10: Failure logging with diagnostics
        System.out.println("  ✗ A* FAILED after " + iter + " iterations");
        System.out.println("    Nodes explored: " + nodesExplored);
        System.out.println("    Nodes generated: " + nodesGenerated);
        System.out.println("    Best distance: " + String.format("%.6f", bestDistanceAchieved));
        System.out.println("    Initial distance: " + String.format("%.6f", initialDistance));
        System.out.println("    Progress: " + String.format("%.1f%%",
                (1.0 - bestDistanceAchieved/initialDistance) * 100));
        System.out.println("    OpenSet size: " + openSet.size());
        System.out.println("    ClosedSet size: " + closedSet.size());

        // Try greedy fallback
        System.out.println("  → Trying greedy fallback...");
        return findPathGreedy(from, to, noFlyZones);
    }

    /**
     * Greedy pathfinding fallback - faster but not optimal
     * Used when A* fails due to complexity
     */
    private List<LngLatAlt> findPathGreedy(LngLatAlt from, LngLatAlt to, List<RestrictedArea> noFlyZones) {
        System.out.println("  → Greedy pathfinding (A* fallback):");
        List<LngLatAlt> path = new ArrayList<>();
        path.add(new LngLatAlt(from.getLng(), from.getLat()));

        double currLng = from.getLng();
        double currLat = from.getLat();
        double targetLng = to.getLng();
        double targetLat = to.getLat();

        int maxIterations = 100000; // Much higher for greedy
        int iter = 0;
        int stuckCounter = 0;
        double lastDistance = distance(currLng, currLat, targetLng, targetLat);

        while (distance(currLng, currLat, targetLng, targetLat) > CLOSE_ENOUGH_THRESHOLD && iter++ < maxIterations) {
            double bestAngle = -1;
            double bestDist = Double.MAX_VALUE;

            // Try all 16 directions
            for (double angle : VALID_ANGLES) {
                double rad = Math.toRadians(angle);
                double newLng = currLng + MOVE_DISTANCE * Math.cos(rad);
                double newLat = currLat + MOVE_DISTANCE * Math.sin(rad);

                if (!isValidMove(currLng, currLat, newLng, newLat, noFlyZones)) continue;

                double dist = distance(newLng, newLat, targetLng, targetLat);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestAngle = angle;
                }
            }

            if (bestAngle < 0) {
                System.out.println("  Greedy stuck at iteration " + iter + " - no valid moves!");
                return Collections.emptyList();
            }

            // Check if we're making progress
            if (bestDist >= lastDistance - 0.0000001) { // Not making progress
                stuckCounter++;
                if (stuckCounter > 100) {
                    System.out.println("  Greedy appears stuck - not making progress");
                    return Collections.emptyList();
                }
            } else {
                stuckCounter = 0; // Reset if we made progress
            }
            lastDistance = bestDist;

            double rad = Math.toRadians(bestAngle);
            currLng += MOVE_DISTANCE * Math.cos(rad);
            currLat += MOVE_DISTANCE * Math.sin(rad);
            path.add(new LngLatAlt(currLng, currLat));

            if (iter % 1000 == 0) {
                System.out.println("  Greedy iteration " + iter + ", distance: " + String.format("%.6f", bestDist));
            }
        }

        System.out.println("  ✓ Greedy path found with " + path.size() + " points");
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
        if (zones == null) return false;

        for (RestrictedArea zone : zones) {
            // Check altitude first (assuming drone altitude is 0 for simplicity)
            // If your drones have altitude, you need to check:
            // if (droneAltitude >= zone.getLimits().getLower() &&
            //     droneAltitude <= zone.getLimits().getUpper())

            if (isPointInPolygon(lng, lat, zone.getVertices())) {
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

        List<String> availableDroneIds = droneAvailabilityService.findAvailableDrones(dispatches);
        if (availableDroneIds.isEmpty()) {
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

        System.out.println("GeoJSON: Delivery centroid: (" + avgLng + ", " + avgLat + ")");

        // Find nearest service point with available drones
        String selectedDroneId = null;
        DroneServicePoint selectedSP = null;
        double nearestDistance = Double.MAX_VALUE;

        for (String droneId : availableDroneIds) {
            Integer spId = findServicePointForDrone(droneId, dronesPerSP);
            if (spId == null) continue;

            DroneServicePoint sp = servicePoints.get(spId);
            if (sp == null || sp.getLocation() == null) continue;

            double dist = distance(sp.getLocation().getLng(), sp.getLocation().getLat(), avgLng, avgLat);
            System.out.println("  SP: " + sp.getName() + " at distance: " + dist);

            if (dist < nearestDistance) {
                nearestDistance = dist;
                selectedDroneId = droneId;
                selectedSP = sp;
            }
        }

        if (selectedDroneId == null || selectedSP == null) {
            System.out.println("GeoJSON: No suitable drone/SP found");
            return createEmptyGeoJson();
        }

        System.out.println("GeoJSON: Selected SP: " + selectedSP.getName() + " (distance: " + nearestDistance + ")");

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
                System.out.println("GeoJSON: No path to delivery " + dispatch.getId());
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
                System.out.println("GeoJSON: Added hover at delivery " + dispatch.getId());
                currentHoverPosition = deliveryPoint;
            }

            // Add return path for last delivery
            if (isLastDelivery) {
                List<LngLatAlt> returnPath = findPathAStar(currentHoverPosition, selectedSP.getLocation(), noFlyZones);
                for (int j = 1; j < returnPath.size(); j++) {
                    LngLatAlt point = returnPath.get(j);
                    coordinates.add(new double[]{point.getLng(), point.getLat()});
                }
                System.out.println("GeoJSON: Added return path");
            }
        }

        System.out.println("GeoJSON: Total coordinates: " + coordinates.size());
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