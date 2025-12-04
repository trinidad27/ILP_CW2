package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/*
 {
    "name": "Drone 1",
    "id": "1",
    "capability": {
      "cooling": true,
      "heating": true,
      "capacity": 4,
      "maxMoves": 2000,
      "costPerMove": 0.01,
      "costInitial": 4.3,
      "costFinal": 6.5
    }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Drone {
    @Valid
    @NotNull
    private String name;
    @Valid
    @NotNull
    private String id;
    @Valid
    @NotNull
    private Capability capability;
    private String status = "IDLE";
    private Double batteryLevel = 100.0;

    private static final double BATTERY_PER_MOVE = 0.1;

    public void consumeBattery(int moves) {
        double consumption = moves * BATTERY_PER_MOVE;
        this.batteryLevel = Math.max(0.0, this.batteryLevel - consumption);
    }

    public void rechargeBattery() {
        this.batteryLevel = 100.0;
    }

    public boolean hasSufficientBattery(int moves) {
        double requiredBattery = moves * BATTERY_PER_MOVE;
        return this.batteryLevel >= requiredBattery;
    }

    public boolean needsCharging(){
        return this.batteryLevel < 20.0;
    }

    public Object getValueByAttribute(String attribute) {
        return switch (attribute) {
            case "capacity" -> this.capability.getCapacity();
            case "cooling" -> this.capability.getCooling();
            case "heating" -> this.capability.getHeating();
            case "maxMoves" -> this.capability.getMaxMoves();
            case "costPerMove" -> this.capability.getCostPerMove();
            case "costInitial" -> this.capability.getCostInitial();
            case "costFinal" -> this.capability.getCostFinal();
            case "batteryLevel" -> this.batteryLevel;
            default -> null;
        };
    }


}
