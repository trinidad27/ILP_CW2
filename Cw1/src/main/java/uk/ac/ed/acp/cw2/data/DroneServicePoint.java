package uk.ac.ed.acp.cw2.data;

import lombok.Data;

@Data
public class DroneServicePoint {
    private String name;
    private Integer id;
    private LngLatAlt location;
}
