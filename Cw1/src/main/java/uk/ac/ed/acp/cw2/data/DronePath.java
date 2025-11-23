package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class DronePath {
    private String droneId;
    private List<Delivery> deliveries;

    @JsonIgnore
    private Integer moves;
    @JsonIgnore
    private Double cost;

}
