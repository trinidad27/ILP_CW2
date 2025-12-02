package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.service.DeliveryPathService;
import uk.ac.ed.acp.cw2.service.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.service.DroneCacheService;
import uk.ac.ed.acp.cw2.service.DroneService;
import uk.ac.ed.acp.cw2.service.DeliverySimulationService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000")
public class DroneController {
    private static final double BATTERY_PER_MOVE = 0.5; // 0.5% battery per move
    private static final double CHARGING_RATE_PER_SECOND = 2.0; // 2% per second

    private final DroneService droneService;
    private final DroneAvailabilityService droneAvailabilityService;
    private final DeliveryPathService deliveryPathService;
    private final DroneCacheService droneCacheService;
    private final DeliverySimulationService deliverySimulationService;

    public DroneController(
            DroneService droneService,
            DroneAvailabilityService droneAvailabilityService,
            DeliveryPathService deliveryPathService,
            DroneCacheService droneCacheService,
            DeliverySimulationService deliverySimulationService) {

        this.droneService = droneService;
        this.droneAvailabilityService = droneAvailabilityService;
        this.deliveryPathService = deliveryPathService;
        this.droneCacheService = droneCacheService;
        this.deliverySimulationService = deliverySimulationService;
    }

    @GetMapping("/dronesWithCooling/{state}")
    public List<Long> dronesWithCooling(@PathVariable boolean state) {
        List<Drone> drones = droneService.getAllDrones();

        List<Long> ids = drones.stream()
                .filter(d -> d.getCapability() != null
                        && d.getCapability().getCooling() == state)
                .map(Drone::getId)
                .map(droneService::parseDroneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ids).getBody();
    }

    @GetMapping("/droneDetails/{id}")
    public ResponseEntity<?> getDroneDetails(@PathVariable String id) {
        long droneId;
        try {
            droneId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Invalid drone ID format");
        }

        Drone drone = droneService.getDroneById(droneId);

        if (drone == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Drone with id " + id + " not found");
        }

        return ResponseEntity.ok(drone);
    }

    @GetMapping("/queryAsPath/{attribute}/{value}")
    public List<Long> queryAsPath(@PathVariable String attribute, @PathVariable String value) {
        return droneService.findDronesByAttributeValue(attribute, value);
    }

    @PostMapping("/query")
    public ResponseEntity<List<Long>> query(@RequestBody List<Query> query){
        try {
            List<Long> ids = droneService.findDronesByQueries(query);
            return ResponseEntity.ok(ids);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    }

    @PostMapping("/queryAvailableDrones")
    public ResponseEntity<List<String>> queryAvailableDrones(@Valid @RequestBody List<MedDispatchRec> dispatches) {
        List<String> availableDroneIds = droneAvailabilityService.findAvailableDrones(dispatches);
        return ResponseEntity.ok(availableDroneIds);

    }

    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<DeliveryPathResponse> calcDeliveryPath(@Valid @RequestBody List<MedDispatchRec> dispatches) {
        return ResponseEntity.ok(deliveryPathService.calcDeliveryPath(dispatches));

    }

    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<Map<String, Object>> calcDeliveryPathAsGeoJson(@Valid @RequestBody List<MedDispatchRec> dispatches) {

        // Calculate path using your existing service
        Map<String, Object> geoJsonResponse = deliveryPathService.calcDeliveryPathAsGeoJson(dispatches);

        // Update drone status and battery in cache
        try {
            Object dronePathsObj = geoJsonResponse.get("dronePaths");
            if (dronePathsObj instanceof List) {
                List<?> dronePaths = (List<?>) dronePathsObj;

                for (Object pathObj : dronePaths) {
                    if (pathObj instanceof Map) {
                        Map<?, ?> path = (Map<?, ?>) pathObj;
                        String droneId = (String) path.get("droneId");
                        Integer moves = (Integer) path.get("moves");

                        if (droneId != null && moves != null) {
                            DroneStatus status = droneCacheService.getDroneStatus(droneId);

                            if (status != null) {
                                // Calculate battery consumption
                                double batteryUsed = moves * BATTERY_PER_MOVE;
                                double newBattery = Math.max(0.0, status.getBatteryLevel() - batteryUsed);

                                // Update status to IN_FLIGHT
                                status.setStatus("IN_FLIGHT");

                                String deliveryIds = dispatches.stream()
                                    .map(d -> String.valueOf(d.getId()))
                                    .collect(Collectors.joining(","));
                                status.setCurrentDeliveryId(deliveryIds);

                                status.setBatteryLevel(newBattery);
                                status.setTotalMoves(status.getTotalMoves() + moves);
                                status.setLastUpdate(java.time.LocalDateTime.now());

                                droneCacheService.updateDroneStatus(status);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok(geoJsonResponse);
    }


    @PostMapping("deliveries")
    public ResponseEntity<DeliveryPathResponse> scheduleDelivery(
            @RequestBody List<MedDispatchRec> dispatches) {
        DeliveryPathResponse response = deliveryPathService.calcDeliveryPath(dispatches);
        return ResponseEntity.ok(
                response
        );
    }

    @PostMapping("/deliveries/preview")
    public ResponseEntity<?> previewDelivery(
            @RequestBody List<MedDispatchRec> dispatches) {
        var geoJson = deliveryPathService.calcDeliveryPathAsGeoJson(dispatches);
        return ResponseEntity.ok(
                geoJson
        );
    }


    /**
     * Sync drones from ILP REST service to Redis cache
     */
    @PostMapping("/syncDronesToCache")
    public ResponseEntity<Map<String, Object>> syncDronesToCache() {

        try {
            if (droneCacheService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "DroneCacheService not available"));
            }

            // Get all drones
            List<Drone> allDrones = droneService.getAllDrones();

            if (allDrones.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "synced", 0,
                        "warning", "No drones found in service"
                ));
            }

            int synced = 0;
            int failed = 0;

            for (Drone drone : allDrones) {
                try {

                    DroneStatus status = new DroneStatus();
                    status.setDroneId(drone.getId());
                    status.setStatus("IDLE");
                    status.setCurrentLng(-3.186874); // Appleton Tower
                    status.setCurrentLat(55.944494);
                    status.setBatteryLevel(100.0);
                    status.setTotalMoves(0);
                    status.setLastUpdate(java.time.LocalDateTime.now());

                    droneCacheService.updateDroneStatus(status);

                    // Verify it was saved
                    DroneStatus verified = droneCacheService.getDroneStatus(drone.getId());
                    if (verified != null) {
                        synced++;
                    } else {
                        failed++;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    failed++;
                }
            }


            return ResponseEntity.ok(Map.of(
                    "synced", synced,
                    "failed", failed,
                    "total", allDrones.size(),
                    "message", "Successfully synced " + synced + " drones to cache"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark delivery as complete and automatically return drone to base
     * This handles the full return flow: IN_FLIGHT → RETURNING → CHARGING → IDLE
     */
    @PostMapping("/completeDelivery/{droneId}")
    public ResponseEntity<Map<String, Object>> completeDelivery(@PathVariable String droneId) {

        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);

            if (status == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Drone not found: " + droneId));
            }

            // Step 1: Mark as RETURNING
            status.setStatus("RETURNING");
            status.setCurrentDeliveryId(null);
            status.setLastUpdate(java.time.LocalDateTime.now());
            droneCacheService.updateDroneStatus(status);

            // Step 2: Simulate arrival at base (reset position)
            status.setCurrentLng(-3.186874); // Appleton Tower
            status.setCurrentLat(55.944494);

            // Step 3: Determine final status based on battery
            String finalStatus;
            if (status.getBatteryLevel() < 100.0) {
                status.setStatus("CHARGING");
                finalStatus = "CHARGING";
            } else {
                status.setStatus("IDLE");
                finalStatus = "IDLE";
            }

            status.setLastUpdate(java.time.LocalDateTime.now());
            droneCacheService.updateDroneStatus(status);

            return ResponseEntity.ok(Map.of(
                    "droneId", droneId,
                    "status", finalStatus,
                    "batteryLevel", status.getBatteryLevel(),
                    "message", "Delivery completed and drone returned to base"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Drone arrives at base (Appleton/Ocean Terminal) and starts charging
     */
    @PostMapping("/arriveAtBase/{droneId}")
    public ResponseEntity<Map<String, Object>> arriveAtBase(@PathVariable String droneId) {

        if (droneCacheService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service not available"));
        }

        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);

            if (status == null) {
                return ResponseEntity.notFound().build();
            }

            status.setCurrentLng(-3.186874);
            status.setCurrentLat(55.944494);

            // Check if needs charging
            if (status.getBatteryLevel() < 100.0) {
                status.setStatus("CHARGING");
            } else {
                status.setStatus("IDLE");
            }

            status.setLastUpdate(java.time.LocalDateTime.now());
            droneCacheService.updateDroneStatus(status);

            return ResponseEntity.ok(Map.of(
                    "droneId", droneId,
                    "status", status.getStatus(),
                    "batteryLevel", status.getBatteryLevel()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Complete charging and set drone to IDLE
     * Call this after the drone has been charging to return it to IDLE status
     */
    @PostMapping("/completeCharging/{droneId}")
    public ResponseEntity<Map<String, Object>> completeCharging(@PathVariable String droneId) {

        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);

            if (status == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Drone not found: " + droneId));
            }

            if (!"CHARGING".equals(status.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Drone is not charging. Current status: " + status.getStatus()));
            }

            // Recharge to 100%
            status.setBatteryLevel(100.0);
            status.setStatus("IDLE");
            status.setLastUpdate(java.time.LocalDateTime.now());

            droneCacheService.updateDroneStatus(status);

            return ResponseEntity.ok(Map.of(
                    "droneId", droneId,
                    "status", "IDLE",
                    "batteryLevel", 100.0,
                    "message", "Charging complete. Drone is now IDLE and ready for delivery."
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Simulate battery charging progress
     * This endpoint can be called to gradually increase battery level
     */
    @PostMapping("/chargeDrone/{droneId}")
    public ResponseEntity<Map<String, Object>> chargeDrone(
            @PathVariable String droneId,
            @RequestParam(defaultValue = "5") int seconds) {

        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);

            if (status == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Drone not found: " + droneId));
            }

            double currentBattery = status.getBatteryLevel();
            double chargeAmount = seconds * CHARGING_RATE_PER_SECOND;
            double newBattery = Math.min(100.0, currentBattery + chargeAmount);

            status.setBatteryLevel(newBattery);
            status.setLastUpdate(java.time.LocalDateTime.now());

            // If battery reaches 100%, automatically set to IDLE
            if (newBattery >= 100.0 && "CHARGING".equals(status.getStatus())) {
                status.setStatus("IDLE");
            }

            droneCacheService.updateDroneStatus(status);


            return ResponseEntity.ok(Map.of(
                    "droneId", droneId,
                    "status", status.getStatus(),
                    "batteryLevel", newBattery,
                    "charged", chargeAmount,
                    "message", newBattery >= 100.0 ? "Fully charged!" : "Charging in progress..."
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get list of service points (Appleton Tower and Ocean Terminal)
     */
    @GetMapping("/service-points")
    public ResponseEntity<List<Map<String, Object>>> getServicePoints() {
        List<Map<String, Object>> servicePoints = List.of(
                Map.of(
                        "id", 1,
                        "name", "Appleton Tower",
                        "location", Map.of(
                                "lng", -3.186874,
                                "lat", 55.944494
                        )
                ),
                Map.of(
                        "id", 2,
                        "name", "Ocean Terminal",
                        "location", Map.of(
                                "lng", -3.177454,
                                "lat", 55.980764
                        )
                )
        );

        return ResponseEntity.ok(servicePoints);
    }

    /**
     * Start a background simulation for a delivery
     * This runs server-side and continues even if the frontend disconnects
     */
    @PostMapping("/startSimulation")
    public ResponseEntity<Map<String, Object>> startSimulation(@RequestBody Map<String, Object> request) {
        try {
            String droneId = (String) request.get("droneId");
            String deliveryId = (String) request.get("deliveryId");
            List<List<Double>> pathCoordinatesRaw = (List<List<Double>>) request.get("pathCoordinates");

            if (droneId == null || pathCoordinatesRaw == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing required fields: droneId and pathCoordinates"));
            }

            // Convert List<List<Double>> to List<double[]>
            List<double[]> pathCoordinates = pathCoordinatesRaw.stream()
                    .map(coord -> new double[]{coord.get(0), coord.get(1)})
                    .collect(Collectors.toList());

            // Check if simulation is already running
            if (deliverySimulationService.isSimulationActive(droneId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Simulation already running for drone " + droneId));
            }

            // Start the simulation asynchronously
            deliverySimulationService.startSimulation(droneId, pathCoordinates, deliveryId);

            return ResponseEntity.ok(Map.of(
                    "message", "Simulation started for drone " + droneId,
                    "droneId", droneId,
                    "pathLength", pathCoordinates.size()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start simulation: " + e.getMessage()));
        }
    }

    /**
     * Stop an active simulation
     */
    @PostMapping("/stopSimulation/{droneId}")
    public ResponseEntity<Map<String, Object>> stopSimulation(@PathVariable String droneId) {
        try {
            deliverySimulationService.stopSimulation(droneId);
            return ResponseEntity.ok(Map.of(
                    "message", "Simulation stopped for drone " + droneId,
                    "droneId", droneId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if a simulation is active for a drone
     */
    @GetMapping("/simulationStatus/{droneId}")
    public ResponseEntity<Map<String, Object>> getSimulationStatus(@PathVariable String droneId) {
        boolean isActive = deliverySimulationService.isSimulationActive(droneId);
        return ResponseEntity.ok(Map.of(
                "droneId", droneId,
                "isActive", isActive
        ));
    }

}
