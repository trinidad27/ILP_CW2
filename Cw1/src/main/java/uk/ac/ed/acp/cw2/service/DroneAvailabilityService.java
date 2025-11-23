package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.data.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DroneAvailabilityService {

    private final Supplier<String> ilpEndpointSupplier;
    private final RestTemplate restTemplate = new RestTemplate();

    public DroneAvailabilityService(Supplier<String> ilpEndpointSupplier) {
        this.ilpEndpointSupplier = ilpEndpointSupplier;
    }

    public List<String> findAvailableDrones(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return Collections.emptyList();
        }

        List<Drone> allDrones = fetchAllDrones();
        Map<String, List<Availability>> availabilityMap = fetchAvailabilityMap();

        Set<String> candidates = allDrones.stream()
                .map(Drone::getId)
                .collect(Collectors.toSet());

        Map<String, Drone> droneMap = allDrones.stream()
                .collect(Collectors.toMap(Drone::getId, d -> d));


        for (MedDispatchRec dispatch : dispatches) {
            if (candidates.isEmpty()) break;

            // Filter by capability
            if (dispatch.getRequirements() != null) {
                Set<String> beforeCap = new HashSet<>(candidates);
                candidates.removeIf(id -> !matchesCapability(droneMap.get(id), dispatch.getRequirements()));
            }

            // Filter by availability
            if (dispatch.getDate() != null && dispatch.getTime() != null) {
                DayOfWeek day = dispatch.getDate().getDayOfWeek();
                LocalTime time = dispatch.getTime();

                Set<String> beforeAvail = new HashSet<>(candidates);
                candidates.removeIf(id -> !isAvailableAt(availabilityMap.get(id), day, time));
            }

            // Filter by cost
            if (dispatch.getRequirements() != null && dispatch.getRequirements().getMaxCost() != null) {
                Set<String> beforeCost = new HashSet<>(candidates);
                candidates.removeIf(id -> !meetsCost(droneMap.get(id), dispatch.getRequirements().getMaxCost()));
            }
        }

        return new ArrayList<>(candidates);
    }

    private boolean matchesCapability(Drone drone, Requirements req) {
        if (drone == null || drone.getCapability() == null) return false;
        Capability cap = drone.getCapability();

        if (req.getCapacity() != null && cap.getCapacity() != null) {
            if (cap.getCapacity() < req.getCapacity()) {
                return false;
            }
        }
        if (Boolean.TRUE.equals(req.getCooling()) && !Boolean.TRUE.equals(cap.getCooling())) {
            return false;
        }
        if (Boolean.TRUE.equals(req.getHeating()) && !Boolean.TRUE.equals(cap.getHeating())) {
            return false;
        }
        return true;
    }

    private boolean isAvailableAt(List<Availability> slots, DayOfWeek day, LocalTime time) {
        if (slots == null || slots.isEmpty()) return false;
        String dayName = day.name(); // "MONDAY", "TUESDAY", etc.
        return slots.stream().anyMatch(s ->
                dayName.equals(s.getDayOfWeek().name())
                        && !time.isBefore(s.getFrom())
                        && !time.isAfter(s.getUntil())
        );
    }

    private boolean meetsCost(Drone drone, Double maxCost) {
        if (drone == null || drone.getCapability() == null) return false;
        Capability c = drone.getCapability();
        double baseCost = (c.getCostInitial() != null ? c.getCostInitial() : 0.0)
                + (c.getCostFinal() != null ? c.getCostFinal() : 0.0);
        return baseCost <= maxCost;
    }

    public List<Drone> fetchAllDrones() {
        String url = ilpEndpointSupplier.get() + "/drones";
        Drone[] drones = restTemplate.getForObject(url, Drone[].class);
        return drones != null ? Arrays.asList(drones) : Collections.emptyList();
    }

    public Map<String, List<Availability>> fetchAvailabilityMap() {
        String url = ilpEndpointSupplier.get() + "/drones-for-service-points";
        DroneForServicePoint[] data = restTemplate.getForObject(url, DroneForServicePoint[].class);
        if (data == null) return Collections.emptyMap();

        Map<String, List<Availability>> map = new HashMap<>();
        for (DroneForServicePoint sp : data) {
            if (sp.getDrones() == null) continue;
            for (DronesAvailability drone : sp.getDrones()) {
                if (drone.getId() == null || drone.getAvailability() == null) continue;
                map.computeIfAbsent(drone.getId(), k -> new ArrayList<>())
                        .addAll(List.of(drone.getAvailability()));
            }
        }
        return map;
    }
}