package uk.ac.ed.acp.cw2.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.DroneStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeliverySimulationService {

    private final DroneCacheService droneCacheService;
    private static final double BATTERY_PER_MOVE = 0.5; // 0.5% battery per move
    private static final long MOVE_INTERVAL_MS = 500; // Update every 500ms

    // Track active simulations
    private final Map<String, Boolean> activeSimulations = new ConcurrentHashMap<>();

    public DeliverySimulationService(DroneCacheService droneCacheService) {
        this.droneCacheService = droneCacheService;
    }

    /**
     * Start a background simulation for a delivery
     * This runs asynchronously and continues even if the frontend disconnects
     */
    @Async
    public void startSimulation(String droneId, List<double[]> pathCoordinates, String deliveryId) {

        // Mark simulation as active
        activeSimulations.put(droneId, true);

        try {
            DroneStatus status = droneCacheService.getDroneStatus(droneId);
            if (status == null) {
                return;
            }

            double battery = status.getBatteryLevel();

            // Set initial status
            status.setStatus("IN_FLIGHT");
            status.setCurrentDeliveryId(deliveryId);
            droneCacheService.updateDroneStatus(status);

            // Simulate flight through each point in the path
            for (double[] coord : pathCoordinates) {

                double lat = coord[0];
                double lng = coord[1];

                // Update position
                status.setCurrentLat(lat);
                status.setCurrentLng(lng);

                // Update battery
                battery = Math.max(0.0, battery - BATTERY_PER_MOVE);
                status.setBatteryLevel(battery);

                // Update cache
                status.setLastUpdate(java.time.LocalDateTime.now());
                droneCacheService.updateDroneStatus(status);

                // Wait before next move
                Thread.sleep(MOVE_INTERVAL_MS);
            }

            // Delivery complete - return to base
            returnToBase(droneId, battery);

        } catch (InterruptedException e) {
            //simulation interrupted
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            activeSimulations.remove(droneId);
        }
    }

    /**
     * Return drone to base and start charging if needed
     */
    private void returnToBase(String droneId, double currentBattery) throws InterruptedException {
        DroneStatus status = droneCacheService.getDroneStatus(droneId);
        if (status == null) return;

        // Set status to RETURNING
        status.setStatus("RETURNING");
        status.setCurrentDeliveryId(null);
        droneCacheService.updateDroneStatus(status);

        // Simulate return journey (2 seconds)
        Thread.sleep(2000);

        status.setCurrentLat(55.944494);
        status.setCurrentLng(-3.186874);

        // Check if charging is needed
        if (currentBattery < 100.0) {
            status.setStatus("CHARGING");
            status.setBatteryLevel(currentBattery);
            droneCacheService.updateDroneStatus(status);

            startCharging(droneId, currentBattery);
        } else {
            status.setStatus("IDLE");
            droneCacheService.updateDroneStatus(status);
        }
    }

    /**
     * Simulate battery charging
     */
    private void startCharging(String droneId, double startBattery) throws InterruptedException {
        double battery = startBattery;

        while (battery < 100.0) {
            // Check if simulation was stopped
            if (!activeSimulations.containsKey(droneId)) {
                return;
            }

            // Charge by 2% per second
            battery = Math.min(100.0, battery + 2.0);

            DroneStatus status = droneCacheService.getDroneStatus(droneId);
            if (status != null) {
                status.setBatteryLevel(battery);
                status.setStatus(battery >= 100.0 ? "IDLE" : "CHARGING");
                status.setLastUpdate(java.time.LocalDateTime.now());
                droneCacheService.updateDroneStatus(status);
            }

            Thread.sleep(1000); // Wait 1 second
        }

    }

    /**
     * Stop an active simulation
     */
    public void stopSimulation(String droneId) {
        activeSimulations.remove(droneId);
    }

    /**
     * Check if a simulation is active for a drone
     */
    public boolean isSimulationActive(String droneId) {
        return activeSimulations.getOrDefault(droneId, false);
    }
}
