package uk.ac.ed.acp.cw2.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Drone;
import uk.ac.ed.acp.cw2.data.DroneStatus;
import uk.ac.ed.acp.cw2.data.LngLatAlt;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to synchronize drones from ILP REST service to Redis cache
 * This bridges your existing ILP data source with the new real-time cache
 */
@Service
public class DroneSyncService {

    @Autowired
    private DroneAvailabilityService droneAvailabilityService;

    @Autowired
    private DroneCacheService droneCacheService;

    // Default starting location (Appleton Tower)
    private static final double DEFAULT_LNG = -3.186874;
    private static final double DEFAULT_LAT = 55.944494;

    /**
     * Manually sync drones from ILP REST service to Redis cache
     * Call this to initialize the cache with available drones
     */
    public SyncResult syncDronesToCache() {
        System.out.println("🔄 Starting drone synchronization from ILP REST service to Redis...");

        try {
            // 1. Fetch all drones from ILP REST service
            List<Drone> ilpDrones = droneAvailabilityService.fetchAllDrones();

            if (ilpDrones == null || ilpDrones.isEmpty()) {
                return new SyncResult(0, 0, "No drones found in ILP service");
            }


            int syncedCount = 0;
            int errorCount = 0;

            // 2. Convert each ILP Drone to DroneStatus and cache it
            for (Drone ilpDrone : ilpDrones) {
                try {
                    DroneStatus cachedStatus = convertToDroneStatus(ilpDrone);
                    droneCacheService.updateDroneStatus(cachedStatus);
                    syncedCount++;



                } catch (Exception e) {
                    errorCount++;
                }
            }


            return new SyncResult(syncedCount, errorCount, "Sync completed successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return new SyncResult(0, 0, "Sync failed: " + e.getMessage());
        }
    }

    /**
     * Auto-sync drones every 5 minutes (optional)
     * Uncomment @Scheduled to enable automatic syncing
     */
    // @Scheduled(fixedRate = 300000) // 5 minutes
    public void autoSync() {
        syncDronesToCache();
    }

    /**
     * Convert ILP Drone to DroneStatus for Redis cache
     */
    private DroneStatus convertToDroneStatus(Drone ilpDrone) {
        DroneStatus status = new DroneStatus();

        status.setDroneId(ilpDrone.getId());
        status.setStatus("IDLE"); // Default status for new drones

        // Use default location (Appleton Tower) if no location specified
        // In reality, you might want to fetch actual locations from service points
        status.setCurrentLng(DEFAULT_LNG);
        status.setCurrentLat(DEFAULT_LAT);

        status.setBatteryLevel(100.0); // Start with full battery
        status.setSpeed(0.0);
        status.setAltitude(0.0);
        status.setCurrentDeliveryId(null);
        status.setLastUpdate(LocalDateTime.now());

        return status;
    }

    /**
     * Check if a drone exists in cache, if not, fetch from ILP and sync
     */
    public DroneStatus ensureDroneInCache(String droneId) {
        // Check cache first
        DroneStatus cached = droneCacheService.getDroneStatus(droneId);

        if (cached != null) {
            return cached;
        }

        // Not in cache - fetch from ILP service
        List<Drone> allDrones = droneAvailabilityService.fetchAllDrones();
        Drone ilpDrone = allDrones.stream()
                .filter(d -> d.getId().equals(droneId))
                .findFirst()
                .orElse(null);

        if (ilpDrone == null) {
            return null;
        }

        // Add to cache
        DroneStatus newStatus = convertToDroneStatus(ilpDrone);
        droneCacheService.updateDroneStatus(newStatus);

        return newStatus;
    }

    /**
     * Update drone location in cache based on service point
     */
    public void updateDroneLocationFromServicePoint(String droneId, LngLatAlt location) {
        DroneStatus status = droneCacheService.getDroneStatus(droneId);

        if (status == null) {
            return;
        }

        if (location != null) {
            status.setCurrentLng(location.getLng());
            status.setCurrentLat(location.getLat());
            if (location.getAlt() != null) {
                status.setAltitude(location.getAlt());
            }
            status.setLastUpdate(LocalDateTime.now());

            droneCacheService.updateDroneStatus(status);
        }
    }

    /**
     * Result class for sync operation
     */
    public static class SyncResult {
        private final int syncedCount;
        private final int errorCount;
        private final String message;

        public SyncResult(int syncedCount, int errorCount, String message) {
            this.syncedCount = syncedCount;
            this.errorCount = errorCount;
            this.message = message;
        }

        public int getSyncedCount() { return syncedCount; }
        public int getErrorCount() { return errorCount; }
        public String getMessage() { return message; }
    }
}