package com.example.timetablescheduler.data;

/**
 * A teacher in the school roster.
 */
public record TeacherDTO(
        String id,    // employee code (e.g. "RVS115")
        String code,  // same as id — kept for API clarity
        String name
) {}

