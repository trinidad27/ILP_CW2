package uk.ac.ed.acp.cw2.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.DroneStatus;
import uk.ac.ed.acp.cw2.service.DroneCacheService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/drones")
@CrossOrigin(origins = "http://localhost:3000")
public class DroneStatusController {

    private final DroneCacheService droneCacheService;

    public DroneStatusController(DroneCacheService droneCacheService) {
        this.droneCacheService = droneCacheService;
    }


    /**
     * POST /api/v1/drones/status - Update drone status
     */
    @PostMapping("/status")
    public ResponseEntity<DroneStatus> updateDroneStatus(@RequestBody DroneStatus status) {
        droneCacheService.updateDroneStatus(status);
        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/v1/drones/stats - Get cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = droneCacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/v1/drones/active - Get all active drones
     */
    @GetMapping("/active")
    public ResponseEntity<List<DroneStatus>> getActiveDrones() {
        List<DroneStatus> drones = droneCacheService.getAllActiveDrones();
        return ResponseEntity.ok(drones);
    }

    /**
     * GET /api/v1/drones/nearby - Get nearby drones
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<DroneStatus>> getNearbyDrones(
            @RequestParam double lng,
            @RequestParam double lat,
            @RequestParam(defaultValue = "5.0") double radiusKm) {

        List<DroneStatus> drones = droneCacheService.getNearbyDrones(lng, lat, radiusKm);
        return ResponseEntity.ok(drones);
    }

    /**
     * GET /api/v1/drones/search/by-status/{status} - Get drones by status
     */
    @GetMapping("/search/by-status/{status}")
    public ResponseEntity<List<DroneStatus>> getDronesByStatus(@PathVariable String status) {
        List<DroneStatus> drones = droneCacheService.getDronesByStatus(status);
        return ResponseEntity.ok(drones);
    }

    /**
     * GET /api/v1/drones/search?droneId=XXX - Get specific drone by query param
     */
    @GetMapping("/search")
    public ResponseEntity<DroneStatus> searchDrone(@RequestParam String droneId) {
        DroneStatus status = droneCacheService.getDroneStatus(droneId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * DELETE /api/v1/drones/cache/clear - Clear all cache
     */
    @DeleteMapping("/cache/clear")
    public ResponseEntity<Void> clearCache() {
        droneCacheService.clearAllDroneCache();
        return ResponseEntity.ok().build();
    }


    /**
     * GET /api/v1/drones/{droneId} - Get specific drone
     */
    @GetMapping("/{droneId}")
    public ResponseEntity<DroneStatus> getDroneById(@PathVariable String droneId) {

        DroneStatus status = droneCacheService.getDroneStatus(droneId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    /**
     * PATCH /api/v1/drones/{droneId}/location - Update location
     */
    @PatchMapping("/{droneId}/location")
    public ResponseEntity<Void> updateLocation(
            @PathVariable String droneId,
            @RequestParam double lng,
            @RequestParam double lat) {

        droneCacheService.updateDroneLocation(droneId, lng, lat);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /api/v1/drones/{droneId}/battery - Update battery
     */
    @PatchMapping("/{droneId}/battery")
    public ResponseEntity<Void> updateBattery(
            @PathVariable String droneId,
            @RequestParam double level) {

        droneCacheService.updateBatteryLevel(droneId, level);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/v1/drones/{droneId} - Remove drone
     */
    @DeleteMapping("/{droneId}")
    public ResponseEntity<Void> removeDrone(@PathVariable String droneId) {
        droneCacheService.removeDrone(droneId);
        return ResponseEntity.ok().build();
    }
}