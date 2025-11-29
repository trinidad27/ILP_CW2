package uk.ac.ed.acp.cw2.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.DroneStatus;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DroneCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String DRONE_STATUS_KEY_PREFIX = "drone:status:";
    private static final String ACTIVE_DRONES_KEY = "drones:active";
    private static final String DRONE_LOCATION_KEY = "drone:locations";
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes

    public DroneCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Update drone status in cache
     */
    public void updateDroneStatus(DroneStatus status) {
        String key = DRONE_STATUS_KEY_PREFIX + status.getDroneId();

        // Store drone status
        redisTemplate.opsForValue().set(key, status, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);

        // Add to active drones set
        redisTemplate.opsForSet().add(ACTIVE_DRONES_KEY, status.getDroneId());

        // Update geospatial index for location-based queries
        if (status.getCurrentLng() != null && status.getCurrentLat() != null) {
            redisTemplate.opsForGeo().add(
                    DRONE_LOCATION_KEY,
                    new org.springframework.data.geo.Point(status.getCurrentLng(), status.getCurrentLat()),
                    status.getDroneId()
            );
        }

        System.out.println("✓ Cached drone status: " + status);
    }

    /**
     * Get drone status from cache
     */
    public DroneStatus getDroneStatus(String droneId) {
        String key = DRONE_STATUS_KEY_PREFIX + droneId;
        Object value = redisTemplate.opsForValue().get(key);

        if (value instanceof DroneStatus) {
            return (DroneStatus) value;
        }
        return null;
    }

    /**
     * Get all active drones
     */
    public List<DroneStatus> getAllActiveDrones() {
        Set<Object> droneIds = redisTemplate.opsForSet().members(ACTIVE_DRONES_KEY);

        if (droneIds == null || droneIds.isEmpty()) {
            return Collections.emptyList();
        }

        return droneIds.stream()
                .map(id -> getDroneStatus(id.toString()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get drones by status (IDLE, IN_FLIGHT, etc.)
     */
    public List<DroneStatus> getDronesByStatus(String status) {
        return getAllActiveDrones().stream()
                .filter(drone -> status.equals(drone.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Get nearby drones within radius (in kilometers)
     */
    public List<DroneStatus> getNearbyDrones(double lng, double lat, double radiusKm) {
        org.springframework.data.geo.Point center =
                new org.springframework.data.geo.Point(lng, lat);

        org.springframework.data.geo.Distance radius =
                new org.springframework.data.geo.Distance(radiusKm,
                        org.springframework.data.geo.Metrics.KILOMETERS);

        org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs args =
                org.springframework.data.redis.connection.RedisGeoCommands
                        .GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(10);

        var results = redisTemplate.opsForGeo()
                .radius(DRONE_LOCATION_KEY, center, radius, args);

        if (results == null) {
            return Collections.emptyList();
        }

        return results.getContent().stream()
                .map(result -> getDroneStatus(result.getContent().getName().toString()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Update drone location only (lightweight update)
     */
    public void updateDroneLocation(String droneId, double lng, double lat) {
        DroneStatus status = getDroneStatus(droneId);
        if (status != null) {
            status.setCurrentLng(lng);
            status.setCurrentLat(lat);
            updateDroneStatus(status);
        }
    }

    /**
     * Update battery level
     */
    public void updateBatteryLevel(String droneId, double batteryLevel) {
        DroneStatus status = getDroneStatus(droneId);
        if (status != null) {
            status.setBatteryLevel(batteryLevel);
            updateDroneStatus(status);
        }
    }

    /**
     * Remove drone from cache (when offline)
     */
    public void removeDrone(String droneId) {
        String key = DRONE_STATUS_KEY_PREFIX + droneId;
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(ACTIVE_DRONES_KEY, droneId);
        redisTemplate.opsForGeo().remove(DRONE_LOCATION_KEY, droneId);

        System.out.println("✓ Removed drone from cache: " + droneId);
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        Long activeDronesCount = redisTemplate.opsForSet().size(ACTIVE_DRONES_KEY);
        stats.put("activeDrones", activeDronesCount != null ? activeDronesCount : 0);

        List<DroneStatus> drones = getAllActiveDrones();
        long inFlightCount = drones.stream()
                .filter(d -> "IN_FLIGHT".equals(d.getStatus()))
                .count();
        long idleCount = drones.stream()
                .filter(d -> "IDLE".equals(d.getStatus()))
                .count();

        stats.put("inFlight", inFlightCount);
        stats.put("idle", idleCount);
        stats.put("totalCached", drones.size());

        return stats;
    }

    /**
     * Clear all drone cache (for testing/maintenance)
     */
    public void clearAllDroneCache() {
        Set<String> keys = redisTemplate.keys(DRONE_STATUS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(ACTIVE_DRONES_KEY);
        redisTemplate.delete(DRONE_LOCATION_KEY);

        System.out.println("✓ Cleared all drone cache");
    }
}