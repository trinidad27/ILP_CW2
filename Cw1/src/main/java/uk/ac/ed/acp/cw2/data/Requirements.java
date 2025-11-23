package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Requirements {
    @Valid
    @NotNull
    private Double capacity;
    @Valid
    private Boolean cooling = false;
    @Valid
    private Boolean heating = false;
    @Valid
    private Double maxCost;


}
