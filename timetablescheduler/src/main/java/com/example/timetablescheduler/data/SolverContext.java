package com.example.timetablescheduler.data;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared in-memory state holding all data loaded from files and the solution produced by the solver.
 * This component is a Spring singleton; state resets on server restart.
 */
@Component
public class SolverContext {

    /** All 41 sections. */
    public List<SectionDTO> sections = new ArrayList<>();

    /**
     * Curriculum by curriculumKey (e.g. "Class 10" -> list of CurriculumItemDTOs).
     */
    public Map<String, List<CurriculumItemDTO>> curriculumByKey;

    /** All 587 assignment rows from TEACHER_CLASS_ALLOCATION.xlsx. */
    public List<AssignmentDTO> assignments = new ArrayList<>();

    /**
     * Subset of assignments that are schedulable:
     * excludes isUnassigned==true and isZeroWorkload==true.
     */
    public List<AssignmentDTO> schedulable = new ArrayList<>();

    /** Teacher registry: code -> TeacherDTO. */
    public Map<String, TeacherDTO> teachers;

    /** All valid teaching slots (break slots excluded). */
    public List<TimeSlotDTO> allSlots = new ArrayList<>();

    /**
     * Slot pairs per dayIndex: map from dayIndex -> list of period-pair arrays.
     * e.g. Mon-Thu: {1 -> [[1,2],[3,4],[5,6],[7,8]]}
     */
    public Map<Integer, List<int[]>> slotPairsByDay;

    // ─── Data quality issues found at load time ─────────────────────────────

    public List<ConflictDTO> dataConflicts = new ArrayList<>();

    // ─── Solution (populated after solve) ───────────────────────────────────

    public List<ScheduledLessonDTO> solution = new ArrayList<>();

    /** "FEASIBLE" | "OPTIMAL" | "INFEASIBLE" | "MODEL_INVALID" | "UNKNOWN" */
    public String solverStatus;

    public long wallTimeMs;

    public double objectiveValue;

    public List<String> schedulingConflicts = new ArrayList<>();

    /** Whether data has been loaded (for API guard). */
    public boolean dataLoaded = false;

    /** Resets solution state for a fresh solve run. */
    public void resetSolution() {
        solution = new ArrayList<>();
        solverStatus = null;
        wallTimeMs = 0;
        objectiveValue = 0;
        schedulingConflicts = new ArrayList<>();
    }
}

