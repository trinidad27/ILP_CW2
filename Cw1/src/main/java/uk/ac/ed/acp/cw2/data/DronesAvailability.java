package uk.ac.ed.acp.cw2.data;

import lombok.Data;

@Data
public class DronesAvailability {
    private String id;
    private Availability[] availability;

}
