package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Query {
    @Valid
    private String attribute;
    @Valid
    private String operator;
    @Valid
    private String value;
}
