package uk.ac.ed.acp.cw2.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.service.DroneSyncService;
import uk.ac.ed.acp.cw2.service.DroneCacheService;

import java.util.Map;

/**
 * Admin endpoints for system management
 */
@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "http://localhost:3000")
public class AdminController {

    @Autowired
    private DroneSyncService droneSyncService;

    @Autowired
    private DroneCacheService droneCacheService;

    /**
     * Sync drones from ILP REST service to Redis cache
     * Call this endpoint to initialize the cache with drones
     */
    @PostMapping("/sync-drones")
    public ResponseEntity<Map<String, Object>> syncDrones() {
        System.out.println("🔄 Admin: Manual drone sync requested");

        DroneSyncService.SyncResult result = droneSyncService.syncDronesToCache();

        Map<String, Object> response = Map.of(
                "syncedCount", result.getSyncedCount(),
                "errorCount", result.getErrorCount(),
                "message", result.getMessage()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all drone cache
     */
    @DeleteMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        System.out.println("🗑️ Admin: Clearing drone cache");

        try {
            droneCacheService.clearAllDroneCache();
            return ResponseEntity.ok(Map.of(
                    "message", "Cache cleared successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get cache statistics
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            Map<String, Object> stats = droneCacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "drone-management-system"
        ));
    }
}