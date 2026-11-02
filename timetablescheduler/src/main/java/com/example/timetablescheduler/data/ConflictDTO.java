package com.example.timetablescheduler.data;

/**
 * A data quality issue or scheduling conflict discovered at load or solve time.
 */
public record ConflictDTO(
        String type,        // "UNASSIGNED_TEACHER" | "ZERO_WORKLOAD" | "NO_ASSIGNMENTS" | "CURRICULUM_MISMATCH"
        String section,
        String subject,
        String teacherCode,
        String message
) {}

