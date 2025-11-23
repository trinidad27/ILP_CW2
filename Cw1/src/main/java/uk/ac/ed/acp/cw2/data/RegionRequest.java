package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Represents a region and a position of a drone
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RegionRequest {
    @Valid
    @NotNull
    private Position position;
    @Valid
    @NotNull
    private Region region;

    public RegionRequest(Position position, Region region) {
        this.position = position;
        this.region = region;
    }
}
