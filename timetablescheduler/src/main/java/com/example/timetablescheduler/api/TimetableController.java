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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Timetable API", description = "Operations for loading data, running the CP-SAT solver, and querying timetables and conflicts")
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

    @Operation(summary = "Load and normalize input data", description = "Parses sections, bell schedules, curriculum, and teacher allocation files into memory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Data loaded successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to load data")
    })
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

    @Operation(summary = "Generate timetable solution", description = "Executes the Google OR-Tools CP-SAT solver against all hard and soft constraints.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solver execution completed (OPTIMAL, FEASIBLE, or INFEASIBLE)"),
            @ApiResponse(responseCode = "400", description = "Data has not been loaded yet"),
            @ApiResponse(responseCode = "500", description = "Solver execution failed unexpectedly")
    })
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

    @Operation(summary = "Get all section timetables", description = "Returns the weekly schedule for every section.")
    @GetMapping("/sections")
    public ResponseEntity<?> getSections() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(reporting.getSectionTimetables());
    }

    @Operation(summary = "Get timetable for a single section", description = "Returns the weekly schedule for the specified section ID (e.g. '1A').")
    @GetMapping("/sections/{id}")
    public ResponseEntity<?> getSection(@Parameter(description = "Section identifier, e.g. 1A, 10B") @PathVariable String id) {
        if (!ctx.dataLoaded) return notLoaded();
        var timetable = reporting.getSectionTimetable(id);
        if (timetable.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timetable);
    }

    // ─── GET /api/timetable/teachers ─────────────────────────────────────────

    @Operation(summary = "Get all teacher timetables", description = "Returns the weekly schedule for every teacher.")
    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(reporting.getTeacherTimetables());
    }

    @Operation(summary = "Get timetable for a single teacher", description = "Returns the weekly schedule for the specified teacher code (e.g. 'T01').")
    @GetMapping("/teachers/{code}")
    public ResponseEntity<?> getTeacher(@Parameter(description = "Teacher code, e.g. T01") @PathVariable String code) {
        if (!ctx.dataLoaded) return notLoaded();
        var timetable = reporting.getTeacherTimetable(code);
        if (timetable.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timetable);
    }

    // ─── GET /api/timetable/conflicts ────────────────────────────────────────

    @Operation(summary = "Get conflict report", description = "Returns data gaps, unassigned slots, teacher workload mismatches, and scheduling conflicts.")
    @GetMapping("/conflicts")
    public ResponseEntity<?> getConflicts() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(conflictReporter.getReport());
    }

    // ─── GET /api/timetable/status ───────────────────────────────────────────

    @Operation(summary = "Get current solver and application status", description = "Returns whether data is loaded, solver status, wall time, and solution summary.")
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

    @Operation(summary = "List all sections metadata", description = "Returns the list of all registered sections.")
    @GetMapping("/sections-list")
    public ResponseEntity<?> getSectionsList() {
        if (!ctx.dataLoaded) return notLoaded();
        return ResponseEntity.ok(ctx.sections);
    }

    // ─── GET /api/timetable/teachers-list ────────────────────────────────────

    @Operation(summary = "List all teachers metadata", description = "Returns the roster of all teachers.")
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

