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
    private static final double[] VALID_ANGLES = {0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
            180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5};

    public DeliveryPathService(Supplier<String> ilpEndpointSupplier,
                               DroneAvailabilityService droneAvailabilityService) {
        this.ilpEndpointSupplier = ilpEndpointSupplier;
        this.droneAvailabilityService = droneAvailabilityService;
    }

    public DeliveryPathResponse calcDeliveryPath(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return new DeliveryPathResponse(0.0, 0, Collections.emptyList());
        }

        // Fetch data
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

        for (String droneId : availableDroneIds) {
            System.out.println("Processing drone: " + droneId);
            if (remaining.isEmpty()) break;

            Drone drone = droneMap.get(droneId);
            System.out.println("  Drone object: " + (drone != null ? drone.getId() : "NULL"));
            if (drone == null || drone.getCapability() == null) continue;

            Integer spId = findServicePointForDrone(droneId, dronesPerSP);
            System.out.println("  Service point ID: " + spId);
            if (spId == null) continue;

            DroneServicePoint sp = servicePoints.get(spId);
            System.out.println("  Service point: " + (sp != null ? sp.getName() : "NULL"));
            if (sp == null || sp.getLocation() == null) continue;

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
        }

        return new DeliveryPathResponse(totalCost, totalMoves, dronePaths);
    }

    private DronePath buildDronePath(Drone drone, DroneServicePoint sp,
                                     List<MedDispatchRec> dispatches, List<RestrictedArea> noFlyZones) {
        Capability cap = drone.getCapability();
        int maxMoves = cap.getMaxMoves() != null ? cap.getMaxMoves() : Integer.MAX_VALUE;

        LngLatAlt current = sp.getLocation();
        List<Delivery> deliveries = new ArrayList<>();
        int movesUsed = 0;
        double cost = cap.getCostInitial() != null ? cap.getCostInitial() : 0.0;
        double costPerMove = cap.getCostPerMove() != null ? cap.getCostPerMove() : 0.0;

        // Sort dispatches by nearest neighbor
        List<MedDispatchRec> sorted = sortByNearest(current, dispatches);

        for (MedDispatchRec dispatch : sorted) {
            LngLatAlt dest = dispatch.getDelivery();
            if (dest == null) continue;

            // Calculate path to delivery
            List<LngLatAlt> pathToDelivery = findPath(dest, current, noFlyZones);
            int movesToDelivery = pathToDelivery.size() - 1;

            // Calculate return path to service point
            List<LngLatAlt> pathBack = findPath(dest, sp.getLocation(), noFlyZones);
            int movesBack = pathBack.size() - 1;

            // Check if we have enough moves
            if (movesUsed + movesToDelivery + movesBack > maxMoves) {
                continue; // Skip this delivery, try next
            }

            // Add hover point (duplicate last point for delivery)
            if (!pathToDelivery.isEmpty()) {
                LngLatAlt lastPoint = pathToDelivery.getLast();
                pathToDelivery.add(new LngLatAlt(lastPoint.getLng(), lastPoint.getLat()));
            }

            deliveries.add(new Delivery(dispatch.getId(), pathToDelivery));
            movesUsed += movesToDelivery;
            cost += movesToDelivery * costPerMove;
            current = dest;
        }

        // Add return path to service point in last delivery
        if (!deliveries.isEmpty()) {
            List<LngLatAlt> returnPath = findPath(current, sp.getLocation(), noFlyZones);
            int returnMoves = returnPath.size() - 1;
            movesUsed += returnMoves;
            cost += returnMoves * costPerMove;

            // Append return path to last delivery
            Delivery lastDelivery = deliveries.getLast();
            lastDelivery.getFlightPath().addAll(returnPath.subList(1, returnPath.size()));
        }

        cost += cap.getCostFinal() != null ? cap.getCostFinal() : 0.0;

        DronePath path = new DronePath();
        path.setDroneId(drone.getId());
        path.setDeliveries(deliveries);
        path.setMoves(movesUsed);
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

    private List<LngLatAlt> findPath(LngLatAlt from, LngLatAlt to, List<RestrictedArea> noFlyZones) {
        System.out.println("findPath called:");
        System.out.println("  From: " + from.getLng() + ", " + from.getLat());
        System.out.println("  To: " + to.getLng() + ", " + to.getLat());

        List<LngLatAlt> path = new ArrayList<>();
        path.add(new LngLatAlt(from.getLng(), from.getLat()));

        double currLng = from.getLng(), currLat = from.getLat();
        double targetLng = to.getLng(), targetLat = to.getLat();

        double initialDistance = distance(currLng, currLat, targetLng, targetLat);
        System.out.println("  Initial distance: " + initialDistance);
        System.out.println("  MOVE_DISTANCE: " + MOVE_DISTANCE);
        int maxIterations = 10000;
        int iter = 0;

        while (distance(currLng, currLat, targetLng, targetLat) > MOVE_DISTANCE && iter++ < maxIterations) {
            double bestAngle = -1;
            double bestDist = Double.MAX_VALUE;

            for (double angle : VALID_ANGLES) {
                double rad = Math.toRadians(angle);
                double newLng = currLng + MOVE_DISTANCE * Math.cos(rad);
                double newLat = currLat + MOVE_DISTANCE * Math.sin(rad);

                if (isInNoFlyZone(newLng, newLat, noFlyZones)) continue;

                double dist = distance(newLng, newLat, targetLng, targetLat);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestAngle = angle;
                }
            }

            if (bestAngle < 0) break; // No valid move found

            double rad = Math.toRadians(bestAngle);
            currLng += MOVE_DISTANCE * Math.cos(rad);
            currLat += MOVE_DISTANCE * Math.sin(rad);
            path.add(new LngLatAlt(currLng, currLat));
        }

        // Add final point
        path.add(new LngLatAlt(targetLng, targetLat));
        return path;
    }

    private boolean isInNoFlyZone(double lng, double lat, List<RestrictedArea> zones) {
        if (zones == null) return false;
        for (RestrictedArea zone : zones) {
            if (isPointInPolygon(lng, lat, zone.getVertices())) {
                return true;
            }
        }
        return false;
    }

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

        String droneId = availableDroneIds.getFirst();
        Drone drone = droneMap.get(droneId);
        if (drone == null || drone.getCapability() == null) {
            return createEmptyGeoJson();
        }

        Integer spId = findServicePointForDrone(droneId, dronesPerSP);
        if (spId == null) return createEmptyGeoJson();

        DroneServicePoint sp = servicePoints.get(spId);
        if (sp == null || sp.getLocation() == null) return createEmptyGeoJson();

        List<double[]> coordinates = new ArrayList<>();
        LngLatAlt current = sp.getLocation();

        coordinates.add(new double[]{current.getLng(), current.getLat()});

        List<MedDispatchRec> sorted = sortByNearest(current, dispatches);

        for (MedDispatchRec dispatch : sorted) {
            LngLatAlt dest = dispatch.getDelivery();
            if (dest == null) continue;

            List<LngLatAlt> pathToDelivery = findPath(dest, current, noFlyZones);

            for (int i = 1; i < pathToDelivery.size(); i++) {
                LngLatAlt point = pathToDelivery.get(i);
                coordinates.add(new double[]{point.getLng(), point.getLat()});
            }

            current = dest;

        }

        List<LngLatAlt> returnPath = findPath(current, sp.getLocation(), noFlyZones);
        for (int i = 1; i < returnPath.size(); i++) {
            LngLatAlt point = returnPath.get(i);
            coordinates.add(new double[]{point.getLng(), point.getLat()});
        }

        // Build GeoJSON LineString
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