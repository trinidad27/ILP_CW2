package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class Capability {
    @Valid
    @NotNull
    private Boolean cooling;
    @Valid
    @NotNull
    private Boolean heating;
    @Valid
    @NotNull
    private Double capacity;
    @Valid
    @NotNull
    private Integer maxMoves;
    @Valid
    @NotNull
    private Double costPerMove;
    @Valid
    @NotNull
    private Double costInitial;
    @Valid
    @NotNull
    private Double costFinal;
}
