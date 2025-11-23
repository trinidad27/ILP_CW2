package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.*;

import java.util.List;
import java.util.Map;

/**
 * Service class for geographical calculations and validations
 */
@Service
public class GeoService {
    private static final double STEP = 0.00015;
    private static final double EPSILON = 1e-9;

    /**
     * Validates the Distance DTO to ensure all required fields are present
     *
     * @param body The Distance DTO to validate
     * @return true if any required field is missing, false otherwise
     */
    public boolean validation(Distance body) { //checking the JSON input has no missing data

        return body == null || body.getPosition1() == null || body.getPosition2() == null ||
                body.getPosition1().getLat() == null || body.getPosition1().getLng() == null ||
                body.getPosition2().getLat() == null || body.getPosition2().getLng() == null;
    }

    /**
     * Calculates the Pythagorean distance between two positions
     *
     * @param position1 The first Position object
     * @param position2 The second Position object
     * @return The computed distance as a double
     */
    public double calculateDistance(Position position1, Position position2) {
        double x1 = position1.getLng();
        double y1 = position1.getLat();
        double x2 = position2.getLng();
        double y2 = position2.getLat();

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    /**
     * Determines if two positions are close to each other within a defined step
     *
     * @param position1 The first Position object
     * @param position2 The second Position object
     * @return true if the positions are close, false otherwise
     */
    public boolean isCloseTo(Position position1, Position position2) {
        return calculateDistance(position1,position2) < STEP;
    }

    /**
     * Validates if the given angle is a multiple of 22.5 degrees
     *
     * @param angle The angle to validate
     * @return true if the angle is valid, false otherwise
     */
    public boolean validAngle(double angle) {
        return Math.abs(angle % 22.5) < EPSILON;
    }

    /**
     * Calculates the next position based on a starting position and an angle
     *
     * @param body The Next DTO containing the start position and angle
     * @return A map with the new longitude and latitude
     */
    public Map<String, Double> nextPosition(Next body) {
        double lng = body.getStart().getLng();
        double lat = body.getStart().getLat();
        double angle = body.getAngle();

        if (!validAngle(angle)) {
            throw new IllegalArgumentException("Invalid angle input");
        }

        //calculate positioning
        double radians = Math.toRadians(angle);
        double deltaLng = STEP * Math.cos(radians);
        double deltaLat = STEP * Math.sin(radians);

        double newLng = lng + deltaLng;
        double newLat = lat + deltaLat;

        //set new position
        return Map.of("lng", newLng, "lat", newLat);
    }

    /**
     * Determines if a point is inside a polygon defined by a list of vertices
     *
     * @param vertices The list of vertices defining the polygon
     * @param xp       The x-coordinate (longitude) of the point
     * @param yp       The y-coordinate (latitude) of the point
     * @return true if the point is inside the polygon, false otherwise
     */
    public boolean isInside(List<Vertex> vertices, Double xp, Double yp) {
        //check if shape has more than 3 vertices
        if (vertices == null || vertices.size() <= 3 || xp == null || yp == null) {
            throw new IllegalArgumentException("Invalid polygon or point input");
        }

        int c = 0;
        int n = vertices.size();

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n; //wrap around

            Double x1 = vertices.get(i).getLng();
            Double y1 = vertices.get(i).getLat();
            Double x2 = vertices.get(j).getLng();
            Double y2 = vertices.get(j).getLat();

            // Check if point is on the edge of the polygon
            double cross = (xp - x1) * (y2 - y1) - (yp - y1) * (x2 - x1);
            if (Math.abs(cross) < EPSILON &&
                    xp >= Math.min(x1, x2) && xp <= Math.max(x1, x2) &&
                    yp >= Math.min(y1, y2) && yp <= Math.max(y1, y2)) {
                return true;
            }

            // Ray casting algorithm
            if (((yp < y1) != (yp < y2)) && (xp < (x2 - x1) * (yp - y1) / (y2 - y1) + x1)) {
                c++;
            }
        }
        return c % 2 == 1; //odd = inside the polygon
    }

    /**
     * Determines if a position is inside a defined region
     *
     * @param body The RegionRequest DTO containing the region and position
     * @return true if the position is inside the region, false otherwise
     */
    public boolean isInRegion(RegionRequest body) {
        double xp = body.getPosition().getLng();
        double yp = body.getPosition().getLat();
        List<Vertex> verticesList = body.getRegion().getVertices();

        if (verticesList.size() < 3)
            throw new IllegalArgumentException("Invalid Input");

        Vertex first = verticesList.get(0);
        Vertex last = verticesList.get(verticesList.size() - 1);

        //check if region is closed (first vertex is equal to the last) with a margin of input error
        boolean isClosed = Math.abs(first.getLng() - last.getLng()) < EPSILON &&
                Math.abs(first.getLat() - last.getLat()) < EPSILON;

        if (!isClosed)
            throw new IllegalArgumentException("Region is not closed");

        return isInside(verticesList, xp, yp);
    }


}
