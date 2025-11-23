package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Represents a polygon showing a named closed area and it's vertices
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Region {
    @NotNull
    private String name;
    @Valid
    @Size(min = 3)
    private List<Vertex> vertices;
}
