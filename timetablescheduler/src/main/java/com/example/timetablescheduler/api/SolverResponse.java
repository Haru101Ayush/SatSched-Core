package com.example.timetablescheduler.api;

import com.example.timetablescheduler.data.*;
import com.example.timetablescheduler.reporting.ConflictReporter;
import com.example.timetablescheduler.reporting.ReportingService;

import java.util.*;

/**
 * Unified response DTO returned by POST /api/timetable/generate.
 */
public class SolverResponse {

    public boolean success;
    public SolverInfo solver;
    public SummaryInfo summary;
    public List<ConflictDTO> dataConflicts;
    public List<String> schedulingConflicts;
    public Map<String, Map<String, List<ReportingService.LessonSlot>>> sectionTimetables;
    public Map<String, Map<String, List<ReportingService.LessonSlot>>> teacherTimetables;
    public ConflictReporter.ValidationReport validation;

    // Factory method
    public static SolverResponse success(
            SolverContext ctx,
            ReportingService reporting,
            ConflictReporter conflictReporter,
            long wallTimeMs,
            int timeLimitSeconds,
            double objectiveValue) {

        SolverResponse r = new SolverResponse();
        r.success = true;

        r.solver = new SolverInfo(
                "Google OR-Tools CP-SAT",
                ctx.solverStatus,
                wallTimeMs,
                timeLimitSeconds,
                objectiveValue
        );

        long scheduledSections = ctx.solution.stream()
                .map(ScheduledLessonDTO::sectionId).distinct().count();
        long unassigned = ctx.assignments.stream().filter(AssignmentDTO::isUnassigned).count();
        long zeroWl = ctx.assignments.stream().filter(a -> !a.isUnassigned() && a.isZeroWorkload()).count();

        r.summary = new SummaryInfo(
                ctx.sections.size(),
                (int) scheduledSections,
                ctx.solution.size(),
                ctx.teachers.size(),
                (int) unassigned,
                (int) zeroWl
        );

        r.dataConflicts = ctx.dataConflicts;
        r.schedulingConflicts = ctx.schedulingConflicts;
        r.sectionTimetables = reporting.getSectionTimetables();
        r.teacherTimetables = reporting.getTeacherTimetables();
        r.validation = conflictReporter.validate();

        return r;
    }

    public static SolverResponse failure(SolverContext ctx, String status, long wallTimeMs, String error) {
        SolverResponse r = new SolverResponse();
        r.success = false;
        r.solver = new SolverInfo("Google OR-Tools CP-SAT", status, wallTimeMs, 300, 0);
        r.dataConflicts = ctx.dataConflicts != null ? ctx.dataConflicts : List.of();
        r.schedulingConflicts = ctx.schedulingConflicts != null
                ? ctx.schedulingConflicts : List.of(error);
        return r;
    }

    public record SolverInfo(String engine, String status, long wallTimeMs,
                             int timeLimitSeconds, double objectiveValue) {}

    public record SummaryInfo(int sectionsTotal, int sectionsScheduled,
                              int totalSlotsScheduled, int teachersInvolved,
                              int unassignedPlaceholderRows, int zeroWorkloadRows) {}
}

