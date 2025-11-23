package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.service.GeoService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeoService
 */
public class GeoServiceTest {
    private GeoService service;

    @BeforeEach
    void setUp() {
        service = new GeoService();
    }

    @DisplayName("Validation test should return true when data is missing")
    @Test
    void validation_shouldReturnTrue_WhenDataIsMissing() {
        Distance body = new Distance();
        body.setPosition1(null);
        body.setPosition2(new Position());

        assertTrue(service.validation(body));
    }

    @DisplayName("Validation test should return false when all data is present")
    @Test
    void validation_True() {
        Distance body = new Distance();
        Position p1 = new Position();
        p1.setLat(55.0);
        p1.setLng(3.0);

        Position p2 = new Position();
        p2.setLat(55.0);
        p2.setLng(4.0);

        body.setPosition1(p1);
        body.setPosition2(p2);

        assertFalse(service.validation(body));
    }

    @DisplayName("Validation test should return true when something is missing")
    @Test
    void validation_False() {
        Distance body = new Distance();
        Position p1 = new Position();

        p1.setLat(null);
        p1.setLng(3.0);

        Position p2 = new Position();
        p2.setLat(55.0);
        p2.setLng(4.0);

        body.setPosition1(p1);
        body.setPosition2(p2);

        assertTrue(service.validation(body));
    }

    @DisplayName("Validation test should return false when all fields are present")
    @Test
    void validation_shouldReturnFalse_WhenAllFieldsPresent() {
        Distance body = new Distance();

        Position p1 = new Position();
        p1.setLat(55.0);
        p1.setLng(3.0);

        Position p2 = new Position();
        p2.setLat(55.0);
        p2.setLng(4.0);

        body.setPosition1(p1);
        body.setPosition2(p2);

        assertFalse(service.validation(body));
    }

    @DisplayName("Calculate distance should return correct value")
    @Test
    void calculateDistance_shouldReturnCorrectValue() {
        Position p1 = new Position();
        p1.setLat(55.0);
        p1.setLng(3.0);

        Position p2 = new Position();
        p2.setLat(55.0);
        p2.setLng(4.0);


        double result = service.calculateDistance(p1,p2);
        assertEquals(1.0, result, 1e-9);
    }

    @DisplayName("isCloseTo should return true when positions are closer than 0.00015")
    @Test
    void isCloseTo_shouldReturnTrueWhenCloseEnough() {
        Position p1 = new Position();
        p1.setLat(55.0);
        p1.setLng(3.0);

        Position p2 = new Position();
        p2.setLat(55.0001);
        p2.setLng(3.0001);


        assertTrue(service.isCloseTo(p1,p2));
    }

    @DisplayName("isCloseTo should return false when positions are farther than 0.00015")
    @Test
    void testIsCloseTo_True(){
        Position p1 = new Position();
        p1.setLat(0.0);
        p1.setLng(0.0);
        Position p2 = new Position();
        p2.setLat(0.0001);
        p2.setLng(0.0001);

        assertTrue(service.isCloseTo(p1, p2));
    }

    @DisplayName("Testing angle is divisible by 22.5")
    @Test
    void testValidAngle_Valid() {
        assertTrue(service.validAngle(45.0));
        assertTrue(service.validAngle(22.5));
    }

    @DisplayName("Testing angle is not divisible by 22.5")
    @Test
    void testValidAngle_Invalid() {
        assertFalse(service.validAngle(30.0));
        assertFalse(service.validAngle(10.0));
    }

    @DisplayName("Testing next position with invalid angle throws exception")
    @Test
    void testNextPosition_InvalidAngle_ThrowsException() {
        Position start = new Position();
        start.setLat(0.0);
        start.setLng(0.0);

        Next next = new Next();
        next.setStart(start);
        next.setAngle(13.0);

        assertThrows(IllegalArgumentException.class, () -> service.nextPosition(next));
    }

    @DisplayName("Testing angle on boundary values")
    @Test
    void testValidAngle_Boundary() {
        assertTrue(service.validAngle(0.0));
        assertTrue(service.validAngle(360.0));
        assertFalse(service.validAngle(22.5000001));
    }

    @DisplayName("Testing next position calculation")
    @Test
    void testNextPosition() {
        Position start = new Position();
        start.setLat(0.0);
        start.setLng(0.0);
        Next next = new Next();
        next.setStart(start);
        next.setAngle(0.0);

        Map<String, Double> result = service.nextPosition(next);
        assertEquals(0.00015, result.get("lng"), 1e-9);
        assertEquals(0.0, result.get("lat"), 1e-9);
    }

    @DisplayName("Testing isInRegion with point inside the region")
    @Test
    void testIsInRegion_Inside() {
        Region region = new Region();
        region.setName("cental");
        region.setVertices(List.of(
                new Vertex(-3.192473,  55.946233),
                new Vertex(-3.192473, 55.942617),
                new Vertex(-3.184319,  55.942617),
                new Vertex(-3.184319, 55.946233),
                new Vertex( -3.192473,  55.946233)
        ));
        Position pos = new Position();
        pos.setLng(-3.188);
        pos.setLat(55.944);
        RegionRequest request = new RegionRequest(pos, region);

        assertTrue(service.isInRegion(request));
    }

    @DisplayName("Testing isInRegion with point on the boundary of the region")
    @Test
    void testIsInRegion_OnBoundary_ShouldReturnTrue() {
        Region region = new Region();
        region.setVertices(List.of(
                new Vertex(0.0, 0.0),
                new Vertex(0.0, 1.0),
                new Vertex(1.0, 1.0),
                new Vertex(1.0, 0.0),
                new Vertex(0.0, 0.0)
        ));
        Position pos = new Position();
        pos.setLng(0.0);
        pos.setLat(0.5);

        RegionRequest request = new RegionRequest(pos, region);

        assertTrue(service.isInRegion(request));
    }

    @DisplayName("Testing isInRegion with point outside the region")
    @Test
    void testIsInRegion_Outside() {
        Region region = new Region();
        region.setName("Square");
        region.setVertices(List.of(
                new Vertex(0.0, 0.0),
                new Vertex(0.0, 1.0),
                new Vertex(1.0, 1.0),
                new Vertex(1.0, 0.0),
                new Vertex(0.0, 0.0)
        ));
        Position pos = new Position();
        pos.setLng(2.0);
        pos.setLat(2.0);
        RegionRequest request = new RegionRequest(pos, region);

        assertFalse(service.isInRegion(request));
    }

    @DisplayName("Testing isInRegion with not closed region throws exception")
    @Test
    void testIsInRegion_NotClosedRegion_Throws() {
        Region region = new Region();
        region.setName("InvalidRegion");
        region.setVertices(List.of(
                new Vertex(0.0, 0.0),
                new Vertex(0.0, 1.0),
                new Vertex(1.0, 1.0)
        ));
        Position pos = new Position();
        pos.setLng(0.5);
        pos.setLat(0.5);
        RegionRequest request = new RegionRequest(pos, region);

        assertThrows(IllegalArgumentException.class, () -> service.isInRegion(request));
    }
}
