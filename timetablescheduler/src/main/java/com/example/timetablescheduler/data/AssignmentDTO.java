package com.example.timetablescheduler.data;

/**
 * A single teaching assignment row from TEACHER_CLASS_ALLOCATION.xlsx.
 * Each row binds one teacher to one subject for one section with a weekly workload.
 */
public record AssignmentDTO(
        String sectionId,
        String subjectName,
        String teacherId,
        String teacherName,
        int periodsPerWeek,
        boolean isUnassigned,   // true when teacherId == "UNASSIGNED-TT"
        boolean isZeroWorkload  // true when periodsPerWeek <= 0
) {}

