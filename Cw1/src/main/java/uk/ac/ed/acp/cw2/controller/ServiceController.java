package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.service.GeoService;
import uk.ac.ed.acp.cw2.data.*;

import java.net.URL;
import java.util.List;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */
@RestController()
@RequestMapping("/api/v1")
public class ServiceController {

    private final GeoService geoService;

    private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

    @Value("${ilp.service.url}")
    public URL serviceUrl;

    public ServiceController(GeoService geoService) {
        this.geoService = geoService;
    }

    /**
     * Returns Welcome from ILP with a service URL link
     * @return HTML string containing the service link
     */
    @GetMapping("/")
    public String index() {
        return "<html><body>" +
                "<h1>Welcome from ILP</h1>" +
                "<h4>ILP-REST-Service-URL:</h4> <a href=\"" + serviceUrl + "\" target=\"_blank\"> " + serviceUrl+ " </a>" +
                "</body></html>";
    }

    /**
     * Returns my student ID
     * @return Student ID as plain text
     */
    @GetMapping("/uid")
    public String uid() {
        return "s2525285";
    }

    /**
     * Calculates the Pythagorean distance between two positions
     * @param body The Distance DTO containing two position objects
     * @return The computed distance between the two positions
     */
    @PostMapping("/distanceTo")
    public ResponseEntity<?> distanceTo(@Valid @RequestBody Distance body) {
        double distance = geoService.calculateDistance(body.getPosition1(), body.getPosition2());
        return ResponseEntity.ok(distance);
    }

    /**
     * Calculates whether two positions are close (< 0.00015)
     * @param body The Distance DTO object containing two Position objects
     * @return True if the two positions are close, otherwise false
     */
    @PostMapping("/isCloseTo")
    public ResponseEntity<?> isCloseTo(@Valid @RequestBody Distance body) {
        boolean isClose = geoService.isCloseTo(body.getPosition1(), body.getPosition2());
        return ResponseEntity.ok(isClose);
    }

    /**
     * Calculates the next position of the drone
     * @param body The Next DTO object containing a Double object and a Start object
     * @return The next position as LngLat for a start position and an angle
     */
    @PostMapping("/nextPosition")
    public ResponseEntity<?> nextPosition(@Valid @RequestBody Next body) {
        return ResponseEntity.ok(geoService.nextPosition(body));
    }

    /**
     * Calculates whether a point is inside the named region (including the border)
     * @param body The RegionRequest DTO object containing a Region object and a Position object
     * @return True if the point is inside,  otherwise false
     */
    @PostMapping("/isInRegion")
    public ResponseEntity<?> isInRegion(@Valid @RequestBody RegionRequest body) {
        boolean contains = geoService.isInRegion(body);
        return ResponseEntity.ok(contains);
    }

}
