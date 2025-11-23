package uk.ac.ed.acp.cw2;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Integration tests for {@link uk.ac.ed.acp.cw2.controller.ServiceController} endpoints
 * Tests REST behaviour for every endpoint
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ServiceControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; //to convert objects to JSON

    @DisplayName("Health endpoint should return UP status")
    @Test
    void testHealth() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"));

    }

    @DisplayName("UID endpoint should return correct student ID")
    @Test
    void testUid() throws Exception{
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/uid"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("s2525285"));
    }

    @DisplayName("DistanceTo endpoint should return valid distance for valid input")
    @Test
    void testDistanceTo_ValidInput() throws Exception {
        String json = """
                {
                "position1": {"lng": -3.192473, "lat": 55.946233},
                "position2": {"lng": -3.192473, "lat": 55.942617}
                }
                """;
        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.notNullValue()));
    }

    @DisplayName("DistanceTo endpoint should return BadRequest for empty input")
    @Test
    void testDistanceTo_EmptyInput() throws Exception {
        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("DistanceTo endpoint should return BadRequest for missing position")
    @Test
    void testDistanceTo_MissingPosition() throws Exception {
        String json = """
                {
                "position2": {"lng": -3.192473, "lat": 55.942617}
                }
                """;
        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("isCloseTo endpoint tests")
    @Test
    void testIsCloseTo_True() throws Exception {
        String json = """
                 {
                "position1": {"lng": -3.192473, "lat": 55.946233},
                "position2": {"lng": -3.192473, "lat": 55.946230}
                }
                """;
        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @DisplayName("isCloseTo endpoint should return false for distant positions")
    @Test
    void testIsCloseTo_False() throws Exception {
        String json = """
                 {
                "position1": {"lng": -3.192473, "lat": 55.946233},
                "position2": {"lng": -4.192473, "lat": 56.946233}
                }
                """;
        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @DisplayName("isCloseTo endpoint should return BadRequest for empty input")
    @Test
    void testIsCloseTo_InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

    }

    @DisplayName("nextPosition endpoint tests")
    @Test
    void testNextPosition_ValidInput() throws Exception {
        String json = """
        {
          "start": {"lng": -3.192473, "lat": 55.946233},
          "angle": 90
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").exists())
                .andExpect(jsonPath("$.lat").exists());
    }

    @DisplayName("nextPosition endpoint should return BadRequest for empty input")
    @Test
    void testNextPosition_EmptyInput() throws Exception {
        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

    }

    @DisplayName("nextPosition endpoint should return BadRequest for missing angle")
    @Test
    void testNextPosition_InvalidInput() throws Exception {
        String json = """
        {
          "start": {"lng": -3.192473, "lat": 55.946233},
          "angle": 13
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }


    @DisplayName("isInRegion endpoint tests")
    @Test
    void testIsInRegion_PointInside() throws Exception {
        String json = """
        {
          "position": {"lng": -3.19, "lat": 55.94},
          "region": {
            "name": "TestRegion",
            "vertices": [
              {"lng": -3.20, "lat": 55.93},
              {"lng": -3.18, "lat": 55.93},
              {"lng": -3.18, "lat": 55.95},
              {"lng": -3.20, "lat": 55.95},
              {"lng": -3.20, "lat": 55.93}
            ]
          }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @DisplayName("isInRegion endpoint should return false for point outside region")
    @Test
    void testIsInRegion_PointOutside() throws Exception {
        String json = """
        {
          "position": {"lng": -4.19, "lat": 55.94},
          "region": {
            "name": "TestRegion",
            "vertices": [
              {"lng": -3.20, "lat": 55.93},
              {"lng": -3.18, "lat": 55.93},
              {"lng": -3.18, "lat": 55.95},
              {"lng": -3.20, "lat": 55.95},
              {"lng": -3.20, "lat": 55.93}
            ]
          }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @DisplayName("isInRegion endpoint should return BadRequest for invalid region")
    @Test
    void testIsInRegion_InvalidRegion() throws Exception {
        String json = """
        {
          "position": {"lng": -3.19, "lat": 55.94},
          "region": {
            "name": "BadRegion",
            "vertices": [
              {"lng": -3.20, "lat": 55.93},
              {"lng": -3.18, "lat": 55.93}
            ]
          }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("isInRegion endpoint should return true for point on the border of the region")
    @Test
    void testIsInRegion_PointOnBorder() throws Exception {
        String json = """
        {
          "position": {"lng": -3.20, "lat": 55.93},
          "region": {
            "name": "TestRegion",
            "vertices": [
              {"lng": -3.20, "lat": 55.93},
              {"lng": -3.18, "lat": 55.93},
              {"lng": -3.18, "lat": 55.95},
              {"lng": -3.20, "lat": 55.95},
              {"lng": -3.20, "lat": 55.93}
            ]
          }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @DisplayName("nextPosition endpoint should ignore extra fields in the request")
    @Test
    void testNextPosition_IgnoresExtraFields() throws Exception {
        String json = """
    {
      "start": {"lng": -3.192473, "lat": 55.946233},
      "angle": 45,
      "extraField": "ignore this"
    }
    """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").exists())
                .andExpect(jsonPath("$.lat").exists());
    }

    @DisplayName("Full workflow integration test")
    @Test
    void testFullWorkflow_ValidFlow() throws Exception {
        // 1. Calculate distance
        String distanceJson = """
        {
          "position1": {"lng": -3.192473, "lat": 55.946233},
          "position2": {"lng": -3.184319, "lat": 55.942617}
        }
        """;
        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(distanceJson))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.notNullValue()));

        // 2. Next position from a point
        String nextPosJson = """
        {
          "start": {"lng": -3.192473, "lat": 55.946233},
          "angle": 0
        }
        """;
        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nextPosJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").exists())
                .andExpect(jsonPath("$.lat").exists());
    }

    @DisplayName("nextPosition endpoint should return BadRequest for invalid angle (>360)")
    @Test
    void testNextPosition_InvalidAngleTooHigh() throws Exception {
        String json = """
    {
      "start": {"lng": -3.192473, "lat": 55.946233},
      "angle": 370
    }
    """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("nextPosition endpoint should return BadRequest for invalid angle (<0)")
    @Test
    void testNextPosition_InvalidAngleNegative() throws Exception {
        String json = """
    {
      "start": {"lng": -3.192473, "lat": 55.946233},
      "angle": -5
    }
    """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("distanceTo endpoint should return BadRequest for invalid latitude (>90)")
    @Test
    void testDistanceTo_InvalidLatitudeTooHigh() throws Exception {
        String json = """
    {
      "position1": {"lng": -3.192473, "lat": 95.0},
      "position2": {"lng": -3.192473, "lat": 55.942617}
    }
    """;

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("distanceTo endpoint should return BadRequest for invalid latitude (<-90)")
    @Test
    void testDistanceTo_InvalidLongitudeTooLow() throws Exception {
        String json = """
    {
      "position1": {"lng": -190.0, "lat": 55.946233},
      "position2": {"lng": -3.192473, "lat": 55.942617}
    }
    """;

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

}
