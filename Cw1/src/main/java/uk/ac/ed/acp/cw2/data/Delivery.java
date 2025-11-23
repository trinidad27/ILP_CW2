package uk.ac.ed.acp.cw2.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Delivery {
    private Integer deliveryId;
    private List<LngLatAlt> flightPath;
}
