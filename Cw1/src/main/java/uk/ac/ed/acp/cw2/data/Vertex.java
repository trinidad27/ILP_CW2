package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Represents a vertex of a region
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Vertex {
    @Min(-180)
    @Max(180)
    @NotNull
    private Double lng;

    @Min(-90)
    @Max(90)
    @NotNull
    private Double lat;

    public Vertex(double lng, double lat) {
        this.lng = lng;
        this.lat = lat;
    }
}
