package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/*
 {
    "name": "Drone 1",
    "id": "1",
    "capability": {
      "cooling": true,
      "heating": true,
      "capacity": 4,
      "maxMoves": 2000,
      "costPerMove": 0.01,
      "costInitial": 4.3,
      "costFinal": 6.5
    }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Drone {
    @Valid
    @NotNull
    private String name;
    @Valid
    @NotNull
    private String id;
    @Valid
    @NotNull
    private Capability capability;

    public Object getValueByAttribute(String attribute) {
        return switch (attribute) {
            case "capacity" -> this.capability.getCapacity();
            case "cooling" -> this.capability.getCooling();
            case "heating" -> this.capability.getHeating();
            case "maxMoves" -> this.capability.getMaxMoves();
            case "costPerMove" -> this.capability.getCostPerMove();
            case "costInitial" -> this.capability.getCostInitial();
            case "costFinal" -> this.capability.getCostFinal();
            default -> null;
        };
    }
}
