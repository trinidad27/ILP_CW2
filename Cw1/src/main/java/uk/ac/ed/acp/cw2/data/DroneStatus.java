package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DroneStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String droneId;
    private String status; // IDLE, IN_FLIGHT, DELIVERING, RETURNING, CHARGING, MAINTENANCE
    private Double currentLng;
    private Double currentLat;
    private Double batteryLevel; // 0.0 to 100.0
    private String currentDeliveryId;
    private Double speed; // meters per second
    private Double altitude; // meters

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdate;

    // Constructors
    public DroneStatus() {
        this.lastUpdate = LocalDateTime.now();
    }

    public DroneStatus(String droneId, String status) {
        this.droneId = droneId;
        this.status = status;
        this.lastUpdate = LocalDateTime.now();
    }

    // Getters and Setters
    public String getDroneId() { return droneId; }
    public void setDroneId(String droneId) { this.droneId = droneId; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status;
        this.lastUpdate = LocalDateTime.now();
    }

    public Double getCurrentLng() { return currentLng; }
    public void setCurrentLng(Double currentLng) { this.currentLng = currentLng; }

    public Double getCurrentLat() { return currentLat; }
    public void setCurrentLat(Double currentLat) { this.currentLat = currentLat; }

    public Double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Double batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getCurrentDeliveryId() { return currentDeliveryId; }
    public void setCurrentDeliveryId(String currentDeliveryId) {
        this.currentDeliveryId = currentDeliveryId;
    }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }

    public LocalDateTime getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(LocalDateTime lastUpdate) { this.lastUpdate = lastUpdate; }

    @Override
    public String toString() {
        return "DroneStatus{" +
                "droneId='" + droneId + '\'' +
                ", status='" + status + '\'' +
                ", position=[" + currentLng + ", " + currentLat + "]" +
                ", battery=" + batteryLevel + "%" +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}