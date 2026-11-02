package com.example.timetablescheduler.data;

/**
 * A valid teaching slot (day + period pair) derived from the bell schedule.
 * Break slots are excluded.
 */
public record TimeSlotDTO(
        String day,       // "MONDAY" .. "SATURDAY"
        int dayIndex,     // 1..6
        int period,       // 1..10
        boolean teaching, // always true here (breaks are excluded)
        String slotPair   // e.g. "1A", "1B" — slot-pair grouping from bell schedule
) {}

