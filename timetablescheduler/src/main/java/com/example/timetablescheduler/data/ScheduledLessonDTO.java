package com.example.timetablescheduler.data;

/**
 * One scheduled lesson from the solved timetable.
 */
public record ScheduledLessonDTO(
        String sectionId,
        String day,
        int period,
        String subjectName,
        String teacherCode,
        String teacherName
) {}

