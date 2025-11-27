package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.DeliveryPathResponse;
import uk.ac.ed.acp.cw2.data.Drone;
import uk.ac.ed.acp.cw2.data.MedDispatchRec;
import uk.ac.ed.acp.cw2.data.Query;
import uk.ac.ed.acp.cw2.service.DeliveryPathService;
import uk.ac.ed.acp.cw2.service.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.service.DroneService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000")
public class DroneController {
    private final DroneService droneService;
    private final DroneAvailabilityService droneAvailabilityService;
    private final DeliveryPathService deliveryPathService;

    public DroneController(DroneService droneService, DroneAvailabilityService droneAvailabilityService, DeliveryPathService deliveryPathService) { this.droneService = droneService;
        this.droneAvailabilityService = droneAvailabilityService;
        this.deliveryPathService = deliveryPathService;
    }

    @GetMapping("/dronesWithCooling/{state}")
    public List<Long> dronesWithCooling(@PathVariable boolean state) {
        List <Drone> drones = droneService.getAllDrones();

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
        List<Long> ids = droneService.findDronesByAttributeValue(attribute, value);
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
        return ResponseEntity.ok(deliveryPathService.calcDeliveryPathAsGeoJson(dispatches));
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

}
