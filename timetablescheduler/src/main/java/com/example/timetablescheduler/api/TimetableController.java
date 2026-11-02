package com.example.timetablescheduler.api;

import com.example.timetablescheduler.data.*;
import com.example.timetablescheduler.loader.DataLoaderService;
import com.example.timetablescheduler.reporting.ConflictReporter;
import com.example.timetablescheduler.reporting.ReportingService;
import com.example.timetablescheduler.solver.TimetableSolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for the timetable solver.
 *
 * Endpoints:
 *   POST /api/timetable/load-data   — Parse all source files; returns summary + dataConflicts
 *   POST /api/timetable/generate    — Build model + solve + extract; returns SolverResponse
 *   GET  /api/timetable/sections    — All section timetables
 *   GET  /api/timetable/sections/{id} — Single section timetable
 *   GET  /api/timetable/teachers    — All teacher timetables
 *   GET  /api/timetable/teachers/{code} — Single teacher timetable
 *   GET  /api/timetable/conflicts   — dataConflicts + schedulingConflicts
 *   GET  /api/timetable/status      — Last solver run status
 */
@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class TimetableController {

    private static final Logger log = LoggerFactory.getLogger(TimetableController.class);

    @Value("${solver.timeLimitSeconds:300}")
    private int timeLimitSeconds;

    private final SolverContext ctx;
    private final DataLoaderService dataLoader;
    private final TimetableSolverService solverService;
    private final ReportingService reporting;
    private final ConflictReporter conflictReporter;

    public TimetableController(SolverContext ctx,
                               DataLoaderService dataLoader,
                               TimetableSolverService solverService,
                               ReportingService reporting,
                               ConflictReporter conflictReporter) {
        this.ctx = ctx;
        this.dataLoader = dataLoader;
        this.solverService = solverService;
        this.reporting = reporting;
        this.conflictReporter = conflictReporter;
    }

    // ─── POST /api/timetable/load-data ───────────────────────────────────────

    @PostMapping("/load-data")
    public ResponseEntity<?> loadData() {
        try {
            DataLoaderService.LoadSummary summary = dataLoader.loadAll();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("loaded", true);
            response.put("summary", Map.of(
                    "sections", summary.sections(),
                    "schedulableAssignments", summary.schedulableAssignments(),
                    "unassignedRows", summary.unassignedRows(),
                    "zeroWorkloadRows", summary.zeroWorkloadRows(),
                    "teachers", summary.teachers()
            ));
            response.put("dataConflicts", ctx.dataConflicts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Data load failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "loaded", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ─── POST /api/timetable/generate ────────────────────────────────────────

    @PostMapping("/generate")
    public ResponseEntity<SolverResponse> generate() {
        if (!ctx.dataLoaded) {
            SolverResponse r = SolverResponse.failure(ctx, "DATA_NOT_LOADED", 0,
                    "Load data first: POST /api/timetable/load-data");
            return ResponseEntity.badRequest().body(r);
        }
        try {
            TimetableSolverService.SolveResult result = solverService.solve();
            if (result.success()) {
                SolverResponse response = SolverResponse.success(
                        ctx, reporting, conflictReporter,
                        result.wallTimeMs(), timeLimitSeconds, result.objectiveValue());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(
                        SolverResponse.failure(ctx, result.status(), result.wallTimeMs(), result.errorMessage()));
            }
        } catch (Exception e) {
            log.error("Generate failed", e);
            return ResponseEntity.internalServerError().body(
                    SolverResponse.failure(ctx, "ERROR", 0, e.getMessage()));
        }
    }

    // ─── GET /api/timetable/sections ─────────────────────────────────────────

    @GetMapping("/sections")
    public ResponseEntity<?> getSections() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(reporting.getSectionTimetables());
    }

    @GetMapping("/sections/{id}")
    public ResponseEntity<?> getSection(@PathVariable String id) {
        if (!ctx.dataLoaded) return notLoaded();
        var timetable = reporting.getSectionTimetable(id);
        if (timetable.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timetable);
    }

    // ─── GET /api/timetable/teachers ─────────────────────────────────────────

    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(reporting.getTeacherTimetables());
    }

    @GetMapping("/teachers/{code}")
    public ResponseEntity<?> getTeacher(@PathVariable String code) {
        if (!ctx.dataLoaded) return notLoaded();
        var timetable = reporting.getTeacherTimetable(code);
        if (timetable.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timetable);
    }

    // ─── GET /api/timetable/conflicts ────────────────────────────────────────

    @GetMapping("/conflicts")
    public ResponseEntity<?> getConflicts() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(conflictReporter.getReport());
    }

    // ─── GET /api/timetable/status ───────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("dataLoaded", ctx.dataLoaded);
        status.put("solverStatus", ctx.solverStatus);
        status.put("wallTimeMs", ctx.wallTimeMs);
        status.put("objectiveValue", ctx.objectiveValue);
        status.put("solutionSize", ctx.solution != null ? ctx.solution.size() : 0);
        status.put("schedulingConflicts", ctx.schedulingConflicts);
        return ResponseEntity.ok(status);
    }

    // ─── GET /api/timetable/sections-list ────────────────────────────────────

    @GetMapping("/sections-list")
    public ResponseEntity<?> getSectionsList() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(ctx.sections);
    }

    // ─── GET /api/timetable/teachers-list ────────────────────────────────────

    @GetMapping("/teachers-list")
    public ResponseEntity<?> getTeachersList() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(ctx.teachers.values());
    }

    private ResponseEntity<Map<String, Object>> notLoaded() {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Data not loaded. Call POST /api/timetable/load-data first."
        ));
    }
}

