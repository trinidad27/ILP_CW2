package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Represents a drone's start position and the angle of the direction they are going
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Next {
    @Valid
    @NotNull
    private Position start;

    @Min(0)
    @Max(360)
    @NotNull
    private Double angle;


}
