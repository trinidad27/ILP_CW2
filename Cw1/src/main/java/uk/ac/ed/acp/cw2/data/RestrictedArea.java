package uk.ac.ed.acp.cw2.data;

import lombok.Data;

import java.util.List;

@Data
public class RestrictedArea {
    private String name;
    private Integer id;
    private Limits limits;
    private List<LngLatAlt> vertices;
}
