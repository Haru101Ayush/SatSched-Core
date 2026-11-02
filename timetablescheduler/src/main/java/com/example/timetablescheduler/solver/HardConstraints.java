package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import com.google.ortools.sat.Literal;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adds all Tier 1 mandatory hard constraints to the CpModel.
 *
 * Constraints implemented:
 *   H1  — No teacher double-booking across sections
 *   H2  — Teacher availability (assumed fully available — A1)
 *   H3  — One lesson per section per valid slot
 *   H4  — No break slots (variables not created — enforced in VariableFactory)
 *   H7  — Daily subject max (periodsPerDay cap)
 *   H8  — Weekly curriculum totals
 *   H10 — Zero-workload excluded (variables not created)
 *   G1  — Games/Library not in periods 1–2
 *   G2  — Class 11 & 12: Games and Library not on same day
 *   L1  — Same-first-word subjects cannot share a day
 *   L2  — No subject spanning last slot before break + first slot after break
 *   L3  — Max 3 consecutive slots with same subject
 *   T2  — Teacher fixed per assignment (by variable design)
 *   DATA-1 — UNASSIGNED-TT excluded (variables not created)
 */
@Component
public class HardConstraints {

    private static final Logger log = LoggerFactory.getLogger(HardConstraints.class);

    private static final Set<String> GAMES_LIBRARY_SUBJECTS = Set.of(
            "games", "library", "games / library"
    );

    // Slots before break on Mon-Thu: last slot before each break
    // Break 1: after period 2 (Short Break 09:05-09:15)
    // Break 2: after period 4 (Lunch Break 10:35-11:05)
    // Break 3: after period 6 (Short Break 12:15-12:20)
    // For Fri-Sat: Break 1 after period 2, Break 2 after period 4
    // Cross-break pairs (L2): (2,3), (4,5), (6,7) for Mon-Thu; (2,3), (4,5) for Fri-Sat
    private static final List<int[]> CROSS_BREAK_PAIRS_MON_THU = List.of(
            new int[]{2, 3}, new int[]{4, 5}, new int[]{6, 7}
    );
    private static final List<int[]> CROSS_BREAK_PAIRS_FRI_SAT = List.of(
            new int[]{2, 3}, new int[]{4, 5}
    );

    public void addAll(CpModel model,
                       SolverContext ctx,
                       Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        addH1_NoTeacherDoubleBooking(model, ctx, vars);
        addH3_OneLessonPerSectionSlot(model, ctx, vars);
        addH7_DailySubjectMax(model, ctx, vars);
        addH8_WeeklyQuota(model, ctx, vars);
        addG1_GamesLibraryNotFirstPair(model, ctx, vars);
        addG2_Class1112GamesLibrarySameDay(model, ctx, vars);
        addL1_SameFirstWordSameDay(model, ctx, vars);
        addL2_NoBreakCrossing(model, ctx, vars);
        addL3_MaxConsecutive(model, ctx, vars);

        log.info("All hard constraints added to model.");
    }

    // Whole-school activities whose instructors are not teacher-booked (A3) — exempt from H1.
    // Must stay in sync with CurriculumReconciler's activity detection so that every subject
    // given a real teacher is also H1-checked. Art Education is a curriculum Subject (real
    // teacher), so it is deliberately NOT here.
    private static final Set<String> ACTIVITY_SUBJECTS = Set.of(
            "games", "games / library", "library", "karate", "gymnastics", "skating",
            "happy feet", "band", "robotics", "abacus", "sanskrit", "french",
            "entrepreneurship"
    );

    private boolean isActivity(String subject) {
        if (subject == null) return false;
        String s = subject.toLowerCase().trim();
        for (String act : ACTIVITY_SUBJECTS) {
            if (s.contains(act)) return true;
        }
        return false;
    }

    // ─── H1: No teacher double-booking ──────────────────────────────────────

    private void addH1_NoTeacherDoubleBooking(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        // For every teacher × day × period: sum of all non-activity BoolVars <= 1
        // Per T6 / AC8: Activity instructors are NOT teacher-booked (whole-school activities run simultaneously)
        int count = 0;
        for (TeacherDTO teacher : ctx.teachers.values()) {
            String tch = teacher.code();
            for (TimeSlotDTO slot : ctx.allSlots) {
                int d = slot.dayIndex();
                int p = slot.period();
                List<BoolVar> teacherSlotVars = new ArrayList<>();
                for (var secEntry : vars.entrySet()) {
                    for (var subEntry : secEntry.getValue().entrySet()) {
                        String subject = subEntry.getKey();
                        if (isActivity(subject)) continue; // T6/AC8: activity instructors run simultaneously

                        Map<String, Map<Integer, Map<Integer, BoolVar>>> tchMap = subEntry.getValue();
                        if (tchMap.containsKey(tch)) {
                            BoolVar v = getVarFromDayPeriod(tchMap.get(tch), d, p);
                            if (v != null) teacherSlotVars.add(v);
                        }
                    }
                }
                if (teacherSlotVars.size() > 1) {
                    model.addAtMostOne(teacherSlotVars.toArray(new Literal[0]));
                    count++;
                }
            }
        }
        log.debug("H1: added {} AddAtMostOne constraints (activity instructors exempted per T6/AC8)", count);
    }

    // ─── H3: One lesson per section per valid slot ───────────────────────────

    private void addH3_OneLessonPerSectionSlot(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        // Each section has at most one lesson per slot (no collisions).
        // For sections with full 52 periods assigned, H8 (sum == 52) automatically forces
        // every valid slot to be filled (exactly one).
        // For sections with < 52 periods (due to UNASSIGNED-TT data gaps), atMostOne allows
        // valid scheduling without artificial infeasibility.
        int count = 0;
        for (SectionDTO sec : ctx.sections) {
            String secId = sec.id();
            Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>> secVars = vars.get(secId);
            if (secVars == null) continue;

            for (TimeSlotDTO slot : ctx.allSlots) {
                int d = slot.dayIndex();
                int p = slot.period();
                List<BoolVar> slotVars = new ArrayList<>();
                for (var subEntry : secVars.entrySet()) {
                    for (var tchEntry : subEntry.getValue().entrySet()) {
                        BoolVar v = getVarFromDayPeriod(tchEntry.getValue(), d, p);
                        if (v != null) slotVars.add(v);
                    }
                }
                if (slotVars.size() > 1) {
                    model.addAtMostOne(slotVars.toArray(new Literal[0]));
                    count++;
                }
            }
        }
        log.debug("H3: added {} slot uniqueness constraints", count);
    }

    // ─── H7: Daily subject max ───────────────────────────────────────────────

    private void addH7_DailySubjectMax(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (AssignmentDTO asgn : ctx.schedulable) {
            CurriculumItemDTO item = resolveCurriculum(ctx, asgn);
            if (item == null) continue;
            int maxPerDay = item.maxPeriodsPerDay();

            Map<Integer, Map<Integer, BoolVar>> dayMap = getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;

            for (int day = 1; day <= 6; day++) {
                List<BoolVar> dayVars = getDayVars(dayMap, day, ctx.allSlots);
                if (!dayVars.isEmpty() && maxPerDay < dayVars.size()) {
                    model.addLinearConstraint(LinearExpr.sum(dayVars.toArray(new BoolVar[0])), 0, maxPerDay);
                    count++;
                }
            }
        }
        log.debug("H7: added {} daily max constraints", count);
    }

    // ─── H8: Weekly curriculum totals ───────────────────────────────────────

    private void addH8_WeeklyQuota(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (AssignmentDTO asgn : ctx.schedulable) {
            Map<Integer, Map<Integer, BoolVar>> dayMap = getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;

            List<BoolVar> weekVars = getAllVars(dayMap);
            if (!weekVars.isEmpty()) {
                model.addEquality(LinearExpr.sum(weekVars.toArray(new BoolVar[0])), asgn.periodsPerWeek());
                count++;
            }
        }
        log.debug("H8: added {} weekly quota constraints", count);
    }

    // ─── G1: Games/Library not in periods 1–2 ───────────────────────────────

    private void addG1_GamesLibraryNotFirstPair(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (AssignmentDTO asgn : ctx.schedulable) {
            if (!isGamesOrLibrary(asgn.subjectName())) continue;
            Map<Integer, Map<Integer, BoolVar>> dayMap = getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;
            for (int day = 1; day <= 6; day++) {
                for (int period : List.of(1, 2)) {
                    BoolVar v = getVar(dayMap.getOrDefault(day, Map.of()), period);
                    if (v != null) {
                        model.addEquality(v, 0);
                        count++;
                    }
                }
            }
        }
        log.debug("G1: fixed {} Games/Library first-pair vars to 0", count);
    }

    // ─── G2: Class 11 & 12: Games and Library not same day ──────────────────

    private void addG2_Class1112GamesLibrarySameDay(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (SectionDTO sec : ctx.sections) {
            if (!isClass11or12(sec.grade())) continue;

            // Standalone Games vs Library subjects (exclude the combined "Games / Library" activity).
            List<AssignmentDTO> gamesAsgns = getSubjectAssignments(ctx, sec.id(), "games").stream()
                    .filter(a -> !a.subjectName().contains("/")).collect(Collectors.toList());
            List<AssignmentDTO> libAsgns = getSubjectAssignments(ctx, sec.id(), "library").stream()
                    .filter(a -> !a.subjectName().contains("/")).collect(Collectors.toList());
            if (gamesAsgns.isEmpty() || libAsgns.isEmpty()) continue;

            // Games and Library may not both appear on the same day (each may hold several periods).
            for (int day = 1; day <= 6; day++) {
                BoolVar gamesPresent = presence(model, ctx, vars, gamesAsgns, day, "g2games_" + sec.id() + "_d" + day);
                BoolVar libPresent = presence(model, ctx, vars, libAsgns, day, "g2lib_" + sec.id() + "_d" + day);
                if (gamesPresent != null && libPresent != null) {
                    model.addAtMostOne(new Literal[]{gamesPresent, libPresent});
                    count++;
                }
            }
        }
        log.debug("G2: added {} Class11/12 games+library same-day constraints", count);
    }

    // ─── L1: Same-first-word subjects cannot share a day ────────────────────

    private void addL1_SameFirstWordSameDay(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (SectionDTO sec : ctx.sections) {
            String secId = sec.id();
            // Group schedulable assignments for this section by first word
            Map<String, List<AssignmentDTO>> groups = new HashMap<>();
            for (AssignmentDTO a : ctx.schedulable) {
                if (!a.sectionId().equals(secId)) continue;
                String fw = firstWord(a.subjectName());
                groups.computeIfAbsent(fw, k -> new ArrayList<>()).add(a);
            }
            for (Map.Entry<String, List<AssignmentDTO>> entry : groups.entrySet()) {
                if (entry.getValue().size() < 2) continue;
                // At most ONE of the same-first-word subjects may appear on a given day
                // (each subject may still use several periods that day, up to its maxPerDay).
                for (int day = 1; day <= 6; day++) {
                    List<Literal> presence = new ArrayList<>();
                    for (AssignmentDTO a : entry.getValue()) {
                        Map<Integer, Map<Integer, BoolVar>> dm = getAsgnDayMap(vars, a);
                        if (dm == null) continue;
                        List<BoolVar> dayVars = getDayVars(dm, day, ctx.allSlots);
                        if (dayVars.isEmpty()) continue;
                        BoolVar present = model.newBoolVar("l1_" + secId + "_" + firstWord(a.subjectName()) + "_" + a.subjectName() + "_d" + day);
                        for (BoolVar v : dayVars) model.addImplication(v, present); // any period => present
                        presence.add(present);
                    }
                    if (presence.size() > 1) {
                        model.addAtMostOne(presence.toArray(new Literal[0]));
                        count++;
                    }
                }
            }
        }
        log.debug("L1: added {} same-first-word same-day constraints", count);
    }

    // ─── L2: No cross-break pairs for same subject ───────────────────────────

    private void addL2_NoBreakCrossing(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        for (AssignmentDTO asgn : ctx.schedulable) {
            Map<Integer, Map<Integer, BoolVar>> dayMap = getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;
            for (int day = 1; day <= 6; day++) {
                List<int[]> crossPairs = day <= 4 ? CROSS_BREAK_PAIRS_MON_THU : CROSS_BREAK_PAIRS_FRI_SAT;
                Map<Integer, BoolVar> periodMap = dayMap.getOrDefault(day, Map.of());
                for (int[] pair : crossPairs) {
                    BoolVar vA = getVar(periodMap, pair[0]);
                    BoolVar vB = getVar(periodMap, pair[1]);
                    if (vA != null && vB != null) {
                        // sum(vA, vB) <= 1  (cannot have same subject spanning break)
                        model.addLinearConstraint(LinearExpr.sum(new BoolVar[]{vA, vB}), 0, 1);
                        count++;
                    }
                }
            }
        }
        log.debug("L2: added {} cross-break constraints", count);
    }

    // ─── L3: Max 3 consecutive slots with same subject ───────────────────────

    private void addL3_MaxConsecutive(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;
        // Consecutive windows of 4 teaching periods (within same day, no break crossing)
        // Mon-Thu: periods 1-8, windows: [1,2,3,4],[2,3,4,5],[3,4,5,6],[4,5,6,7],[5,6,7,8]
        // But breaks at 2|3 and 4|5 and 6|7, so only within-block windows count
        // Effective contiguous ranges: [1,2], [3,4], [5,6], [7,8] (break-split)
        // Windows of 4 consecutive teaching-only slots (ignoring breaks as separators):
        // Since each block segment has at most 2 periods, max consecutive run is 2 without breaks
        // So L3 is naturally satisfied unless breaks don't interrupt. We still add windows.

        for (AssignmentDTO asgn : ctx.schedulable) {
            Map<Integer, Map<Integer, BoolVar>> dayMap = getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;
            for (int day = 1; day <= 6; day++) {
                // Get teaching periods for this day in order
                final int dayFinal = day;
                List<Integer> dayPeriods = ctx.allSlots.stream()
                        .filter(s -> s.dayIndex() == dayFinal)
                        .map(TimeSlotDTO::period)
                        .sorted()
                        .collect(Collectors.toList());
                Map<Integer, BoolVar> periodMap = dayMap.getOrDefault(day, Map.of());
                // Window of 4
                for (int i = 0; i <= dayPeriods.size() - 4; i++) {
                    List<BoolVar> window = new ArrayList<>();
                    for (int j = i; j < i + 4; j++) {
                        BoolVar v = getVar(periodMap, dayPeriods.get(j));
                        if (v != null) window.add(v);
                    }
                    if (window.size() == 4) {
                        model.addLinearConstraint(LinearExpr.sum(window.toArray(new BoolVar[0])), 0, 3);
                        count++;
                    }
                }
            }
        }
        log.debug("L3: added {} max-consecutive constraints", count);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** BoolVar forced to 1 when any period of the given assignments is used on the day; null if none. */
    private BoolVar presence(CpModel model, SolverContext ctx,
                             Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars,
                             List<AssignmentDTO> asgns, int day, String name) {
        List<BoolVar> dayVars = new ArrayList<>();
        for (AssignmentDTO a : asgns) {
            Map<Integer, Map<Integer, BoolVar>> dm = getAsgnDayMap(vars, a);
            if (dm != null) dayVars.addAll(getDayVars(dm, day, ctx.allSlots));
        }
        if (dayVars.isEmpty()) return null;
        BoolVar p = model.newBoolVar(name);
        for (BoolVar v : dayVars) model.addImplication(v, p);
        return p;
    }

    private boolean isGamesOrLibrary(String subject) {
        return GAMES_LIBRARY_SUBJECTS.contains(subject.toLowerCase().trim());
    }

    private boolean isClass11or12(String grade) {
        return "11".equals(grade) || "12".equals(grade);
    }

    private String firstWord(String subject) {
        if (subject == null || subject.isBlank()) return "";
        return subject.trim().split("\\s+")[0].toLowerCase();
    }

    private List<AssignmentDTO> getSubjectAssignments(SolverContext ctx, String sectionId, String subjectKeyword) {
        return ctx.schedulable.stream()
                .filter(a -> a.sectionId().equals(sectionId)
                        && a.subjectName().toLowerCase().contains(subjectKeyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    private CurriculumItemDTO resolveCurriculum(SolverContext ctx, AssignmentDTO asgn) {
        // Find the section
        for (SectionDTO sec : ctx.sections) {
            if (sec.id().equals(asgn.sectionId())) {
                List<CurriculumItemDTO> items = ctx.curriculumByKey != null
                        ? ctx.curriculumByKey.get(sec.curriculumKey()) : null;
                if (items == null) return null;
                for (CurriculumItemDTO item : items) {
                    if (item.subjectName().equalsIgnoreCase(asgn.subjectName())
                            || normalize(item.subjectName()).contains(normalize(asgn.subjectName()))
                            || normalize(asgn.subjectName()).contains(normalize(item.subjectName()))) {
                        return item;
                    }
                }
                // Not found in curriculum — use safe default
                return new CurriculumItemDTO(asgn.subjectName(), asgn.periodsPerWeek(), 2, "Subject");
            }
        }
        return null;
    }

    /** Returns all BoolVars for (dayIndex, period) from the assignment's day->period map. */
    Map<Integer, Map<Integer, BoolVar>> getAsgnDayMap(
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars,
            AssignmentDTO asgn) {
        try {
            return vars.get(asgn.sectionId()).get(asgn.subjectName()).get(asgn.teacherId());
        } catch (NullPointerException e) {
            return null;
        }
    }

    List<BoolVar> getDayVars(Map<Integer, Map<Integer, BoolVar>> dayMap, int day, List<TimeSlotDTO> allSlots) {
        Map<Integer, BoolVar> periodMap = dayMap.getOrDefault(day, Map.of());
        List<BoolVar> result = new ArrayList<>();
        for (TimeSlotDTO slot : allSlots) {
            if (slot.dayIndex() == day) {
                BoolVar v = periodMap.get(slot.period());
                if (v != null) result.add(v);
            }
        }
        return result;
    }

    List<BoolVar> getAllVars(Map<Integer, Map<Integer, BoolVar>> dayMap) {
        List<BoolVar> result = new ArrayList<>();
        for (Map<Integer, BoolVar> pm : dayMap.values()) {
            result.addAll(pm.values());
        }
        return result;
    }

    private BoolVar getVar(Map<Integer, BoolVar> map, int period) {
        return map.get(period);
    }

    /** Helper to look up a var by (day, period) from the day->period nested map. */
    private BoolVar getVarFromDayPeriod(Map<Integer, Map<Integer, BoolVar>> dayPeriodMap, int day, int period) {
        Map<Integer, BoolVar> pm = dayPeriodMap.get(day);
        return pm == null ? null : pm.get(period);
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }
}
