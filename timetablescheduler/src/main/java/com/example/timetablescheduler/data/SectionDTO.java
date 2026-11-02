package com.example.timetablescheduler.data;

/**
 * Represents one school section (e.g. "Grade 10 - Lily").
 */
public record SectionDTO(
        String id,
        String displayName,
        String grade,
        String section,
        String curriculumKey,
        int capacity
) {}

