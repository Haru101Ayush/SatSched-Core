package com.example.timetablescheduler.data;

/**
 * A single curriculum entry for a grade/class.
 * e.g. Mathematics – 2 periods/day, 9 periods/week, type="Subject"
 */
public record CurriculumItemDTO(
        String subjectName,
        int periodsPerWeek,
        int maxPeriodsPerDay,
        String type           // "Subject" | "Activity Group"
) {}

