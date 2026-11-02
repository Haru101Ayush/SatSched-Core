package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates loading of all data sources into SolverContext.
 * Call loadAll() once at startup or via the /load-data endpoint.
 */
@Service
public class DataLoaderService {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderService.class);

    // Default paths relative to the project root / working directory
    @Value("${data.base.path:}")
    private String basePath;

    private final SolverContext ctx;
    private final SectionsLoader sectionsLoader;
    private final BellScheduleLoader bellLoader;
    private final CurriculumLoader curriculumLoader;
    private final AssignmentExcelLoader excelLoader;
    private final DataGapAnalyzer gapAnalyzer;
    private final CurriculumReconciler reconciler;

    public DataLoaderService(SolverContext ctx,
                             SectionsLoader sectionsLoader,
                             BellScheduleLoader bellLoader,
                             CurriculumLoader curriculumLoader,
                             AssignmentExcelLoader excelLoader,
                             DataGapAnalyzer gapAnalyzer,
                             CurriculumReconciler reconciler) {
        this.ctx = ctx;
        this.sectionsLoader = sectionsLoader;
        this.bellLoader = bellLoader;
        this.curriculumLoader = curriculumLoader;
        this.excelLoader = excelLoader;
        this.gapAnalyzer = gapAnalyzer;
        this.reconciler = reconciler;
    }

    public LoadSummary loadAll() throws Exception {
        log.info("Starting data load from base path: '{}'", basePath.isEmpty() ? "<default>" : basePath);

        String bp = basePath.isEmpty() ? "" : (basePath.endsWith("/") ? basePath : basePath + "/");

        // 1. Sections
        String sectionsFile = resolve(bp, "datasets/sections.json");
        ctx.sections = sectionsLoader.load(sectionsFile);

        // 2. Bell schedule
        String bellFile = resolve(bp, "datasets/bell-schedule.json");
        BellScheduleLoader.Result bellResult = bellLoader.load(bellFile);
        ctx.allSlots = bellResult.allSlots();
        ctx.slotPairsByDay = bellResult.slotPairsByDay();

        // 3. Curriculum
        String currFile = resolve(bp, "CLASS_WISE_SUBJECTS.md");
        ctx.curriculumByKey = curriculumLoader.load(currFile);

        // 4. Excel assignments
        String xlsxFile = resolve(bp, "TEACHER_CLASS_ALLOCATION.xlsx");
        AssignmentExcelLoader.Result excelResult = excelLoader.load(xlsxFile, ctx.sections);
        ctx.assignments = excelResult.assignments();
        ctx.teachers = excelResult.teachers();

        // 5. Analyze raw data gaps (UNASSIGNED-TT + zero-workload rows)
        ctx.dataConflicts = gapAnalyzer.analyze(ctx.sections, ctx.assignments, ctx.curriculumByKey);

        // 6. Rebuild schedulable demand from the authoritative curriculum (52 ppw/section),
        //    resolving teachers from Excel and reporting coverage gaps.
        reconciler.reconcile(ctx);

        ctx.dataLoaded = true;

        long unassignedRows = ctx.assignments.stream().filter(AssignmentDTO::isUnassigned).count();
        long zeroRows = ctx.assignments.stream().filter(a -> !a.isUnassigned() && a.isZeroWorkload()).count();

        log.info("Data load complete: {} sections, {} total assignments, {} schedulable, {} teachers",
                ctx.sections.size(), ctx.assignments.size(), ctx.schedulable.size(), ctx.teachers.size());

        return new LoadSummary(
                ctx.sections.size(),
                ctx.schedulable.size(),
                (int) unassignedRows,
                (int) zeroRows,
                ctx.teachers.size(),
                ctx.dataConflicts.size()
        );
    }

    private String resolve(String basePath, String relativePath) {
        if (basePath.isEmpty()) return relativePath;
        return basePath + relativePath;
    }

    public record LoadSummary(
            int sections,
            int schedulableAssignments,
            int unassignedRows,
            int zeroWorkloadRows,
            int teachers,
            int dataConflicts
    ) {}
}

