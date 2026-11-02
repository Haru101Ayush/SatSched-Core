package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrates the full solve cycle:
 *   1. Build CpModel via ConstraintBuilder
 *   2. Run CpSolver
 *   3. Extract solution via SolutionExtractor
 *   4. Populate SolverContext with result status
 */
@Service
public class TimetableSolverService {

    private static final Logger log = LoggerFactory.getLogger(TimetableSolverService.class);

    @Value("${solver.timeLimitSeconds:300}")
    private int timeLimitSeconds;

    // 0 = use all available CPU cores
    @Value("${solver.numWorkers:0}")
    private int numWorkers;

    // Stop once the incumbent is within this fraction of the proven bound. The PR-MATH objective
    // is a soft preference, so closing the last few points to prove OPTIMAL is not worth the time.
    @Value("${solver.relativeGapLimit:0.1}")
    private double relativeGapLimit;

    @Value("${solver.logProgress:false}")
    private boolean logProgress;

    private final SolverContext ctx;
    private final ConstraintBuilder constraintBuilder;
    private final SolutionExtractor solutionExtractor;

    public TimetableSolverService(SolverContext ctx,
                                  ConstraintBuilder constraintBuilder,
                                  SolutionExtractor solutionExtractor) {
        this.ctx = ctx;
        this.constraintBuilder = constraintBuilder;
        this.solutionExtractor = solutionExtractor;
    }

    public SolveResult solve() {
        if (!ctx.dataLoaded) {
            return new SolveResult(false, "DATA_NOT_LOADED", 0, 0,
                    "Data has not been loaded. Call POST /api/timetable/load-data first.");
        }

        ctx.resetSolution();
        log.info("Starting CP-SAT solve: {} schedulable assignments, time limit {}s",
                ctx.schedulable.size(), timeLimitSeconds);

        CpModel model = new CpModel();
        Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars =
                constraintBuilder.build(model, ctx);

        int workers = numWorkers > 0 ? numWorkers : Runtime.getRuntime().availableProcessors();
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(timeLimitSeconds);
        solver.getParameters().setNumSearchWorkers(workers);
        if (relativeGapLimit > 0) solver.getParameters().setRelativeGapLimit(relativeGapLimit);
        solver.getParameters().setLogSearchProgress(logProgress);
        log.info("CP-SAT: {} workers, relativeGapLimit={}, timeLimit={}s", workers, relativeGapLimit, timeLimitSeconds);

        long startMs = System.currentTimeMillis();
        CpSolverStatus status = solver.solve(model);
        ctx.wallTimeMs = System.currentTimeMillis() - startMs;

        log.info("Solver finished in {}ms with status: {}", ctx.wallTimeMs, status);

        switch (status) {
            case OPTIMAL, FEASIBLE -> {
                ctx.solverStatus = status.name();
                ctx.objectiveValue = solver.objectiveValue();
                solutionExtractor.extract(solver, vars, ctx);
                return new SolveResult(true, status.name(), ctx.wallTimeMs, ctx.objectiveValue, null);
            }
            case INFEASIBLE -> {
                ctx.solverStatus = "INFEASIBLE";
                explainInfeasibility(ctx);
                return new SolveResult(false, "INFEASIBLE", ctx.wallTimeMs, 0,
                        "Model is infeasible — capacity vs demand conflicts detected.");
            }
            default -> {
                ctx.solverStatus = status.name();
                String msg = "Solver returned status: " + status.name();
                ctx.schedulingConflicts.add(msg);
                return new SolveResult(false, status.name(), ctx.wallTimeMs, 0, msg);
            }
        }
    }

    private void explainInfeasibility(SolverContext ctx) {
        ctx.schedulingConflicts.clear();

        // Check teacher workload capacity (> 52 slots)
        Map<String, Integer> teacherLoads = new java.util.HashMap<>();
        Map<String, java.util.Set<String>> teacherSections = new java.util.HashMap<>();
        for (AssignmentDTO a : ctx.schedulable) {
            teacherLoads.merge(a.teacherId(), a.periodsPerWeek(), Integer::sum);
            teacherSections.computeIfAbsent(a.teacherId(), k -> new java.util.HashSet<>()).add(a.sectionId());
        }

        for (var entry : teacherLoads.entrySet()) {
            if (entry.getValue() > 52) {
                TeacherDTO t = ctx.teachers.get(entry.getKey());
                String tName = t != null ? t.name() : entry.getKey();
                var secs = teacherSections.getOrDefault(entry.getKey(), java.util.Set.of());
                ctx.schedulingConflicts.add(String.format(
                        "Teacher %s (%s): %d periods required across %d sections %s but weekly capacity is 52.",
                        entry.getKey(), tName, entry.getValue(), secs.size(), secs));
            }
        }

        // Check section workload capacity (> 52 slots)
        Map<String, Integer> secLoads = new java.util.HashMap<>();
        for (AssignmentDTO a : ctx.schedulable) {
            secLoads.merge(a.sectionId(), a.periodsPerWeek(), Integer::sum);
        }
        for (var entry : secLoads.entrySet()) {
            if (entry.getValue() > 52) {
                ctx.schedulingConflicts.add(String.format(
                        "Section %s: %d total periods required from assigned subjects, exceeding weekly schedule capacity of 52 slots.",
                        entry.getKey(), entry.getValue()));
            }
        }

        if (ctx.schedulingConflicts.isEmpty()) {
            ctx.schedulingConflicts.add("Model is infeasible — overlapping teacher time restrictions or tight block layout constraints.");
        }
    }

    public record SolveResult(
            boolean success,
            String status,
            long wallTimeMs,
            double objectiveValue,
            String errorMessage
    ) {}
}

