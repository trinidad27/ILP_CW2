package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MedDispatchRec {
    @Valid
    @NotNull
    private Integer id;
    @Valid
    private LocalDate date;
    @Valid
    private LocalTime time;
    @Valid
    private Requirements requirements;
    @Valid
    private LngLatAlt delivery;


}
