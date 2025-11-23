package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.data.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DroneService {

    private final Supplier<String> ilpEndpointSupplier;
    private final RestTemplate restTemplate = new RestTemplate();

    public DroneService(Supplier<String> ilpEndpointSupplier) {

        this.ilpEndpointSupplier = ilpEndpointSupplier;
    }

    public List<Drone> getAllDrones() {
        String base = ilpEndpointSupplier.get();
        String url = base + "/drones";

        Drone[] drones = restTemplate.getForObject(url, Drone[].class);

        if (drones == null) {
            return List.of();
        }
        return Arrays.asList(drones);
    }

    public List<DronesAvailability> getAllDronesAvailability() {
        String base = ilpEndpointSupplier.get();
        String url = base + "/drones-for-service-points";

        DronesAvailability[] dronesAvailability = restTemplate.getForObject(url, DronesAvailability[].class);

        if (dronesAvailability == null) {
            return List.of();
        }
        return Arrays.asList(dronesAvailability);
    }

    public List<DroneServicePoint> getAllServicePoints() {
        String base = ilpEndpointSupplier.get();
        String url = base + "/service-points";

        DroneServicePoint[] droneServicePoints = restTemplate.getForObject(url, DroneServicePoint[].class);

        if (droneServicePoints == null) {
            return List.of();
        }
        return Arrays.asList(droneServicePoints);
    }
    public Long parseDroneId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Long.valueOf(id);

    } catch (NumberFormatException e) {
            return null; //can change to illegal argument exception if needed
        }
    }

    public Drone getDroneById(Long droneId) {
        List<Drone> drones = getAllDrones();
        return drones.stream()
                .filter(drone -> {
                    try {
                        return Long.valueOf(drone.getId()).equals(droneId);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    public List<Long> findDronesByAttributeValue(String attribute, String value) {
        List<Drone> drones = getAllDrones();
        List<Long> result = new ArrayList<>();

        for (Drone d : drones) {
                boolean match = switch (attribute) {
                case "id" -> value.equals(String.valueOf(d.getId()));
                case "name" -> value.equals(d.getName());
                case "cooling" -> Boolean.parseBoolean(value) == d.getCapability().getCooling();
                case "heating" -> Boolean.parseBoolean(value) == d.getCapability().getHeating();
                case "capacity" -> Double.parseDouble(value) == d.getCapability().getCapacity();
                case "maxMoves" -> Double.parseDouble(value) == d.getCapability().getMaxMoves();
                case "costPerMove" -> Double.parseDouble(value) == d.getCapability().getCostPerMove();
                case "costInitial" -> Double.parseDouble(value) == d.getCapability().getCostInitial();
                case "costFinal" -> Double.parseDouble(value) == d.getCapability().getCostFinal();
                default -> false;
            };

                if (match) {
                    result.add(Long.valueOf(d.getId()));
                }
        }
    return result;
    }

    public List<Long> findDronesByQueries(List<Query> query) {
        List<Drone> drones = getAllDrones();
        return drones.stream()
                .filter(drone -> query.stream().allMatch(attr -> matches(drone, attr)))
                .map(drone -> Long.valueOf(drone.getId()))
                .collect(Collectors.toList());

    }

    private boolean matches( Drone drone, Query attr) {
        String attribute = attr.getAttribute();
        String operator = attr.getOperator();
        String value = attr.getValue();

        Object droneValue = drone.getValueByAttribute(attribute);

        if (droneValue instanceof Number) {
            double d = ((Number) droneValue).doubleValue();
            double v = Double.parseDouble(value);
            return switch (operator) {
                case "=" -> d == v;
                case "!=" -> d != v;
                case "<" -> d < v;
                case ">" -> d > v;
                default -> false;
            };
        } else if (droneValue instanceof Boolean) {
                boolean b = (Boolean) droneValue;
                boolean vBool = Boolean.parseBoolean(value);
                return b == vBool;
            } else {
                return operator.equals("=") && droneValue.toString().equals(value);
            }

    }

}
