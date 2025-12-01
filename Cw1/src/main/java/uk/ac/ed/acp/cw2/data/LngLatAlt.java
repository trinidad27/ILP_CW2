package uk.ac.ed.acp.cw2.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LngLatAlt {
    private Double lng;
    private Double lat;
    private Double alt;

    public LngLatAlt(Double lng, Double lat) {
        this.lng = lng;
        this.lat = lat;
        this.alt = 0.0;
    }
}
