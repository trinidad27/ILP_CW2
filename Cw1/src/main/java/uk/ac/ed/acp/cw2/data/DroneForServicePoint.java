package uk.ac.ed.acp.cw2.data;

import lombok.Data;

@Data
public class DroneForServicePoint {
    private Integer servicePointId;
    private DronesAvailability[] drones;
}
