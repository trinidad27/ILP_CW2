package uk.ac.ed.acp.cw2.data;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class Availability {
    private DayOfWeek dayOfWeek;
    private LocalTime from;
    private LocalTime until;

    public Availability(DayOfWeek day, LocalTime from, LocalTime until) {
        this.dayOfWeek = getDayOfWeek();
        this.from = from;
        this.until = until;
    }
}
