package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Represents two positions of a drone used for distance calculations
 */
@JsonIgnoreProperties(ignoreUnknown = true) //ok to ignore unknown properties in JSON input
@Data
public class Distance {
    @Valid
    @NotNull
    private Position position1;
    @Valid
    @NotNull
    private Position position2;


}
