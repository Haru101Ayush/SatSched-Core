package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adds Tier 2 block-layout constraints (H5, B1, B4) to the CpModel.
 *
 * Block subjects: those with maxPeriodsPerDay >= 2.
 *
 * H5/B1 strategy:
 *   For each block assignment + day, introduce a BoolVar blockVar per slot-pair.
 *   blockVar=1 iff X[day, pA]=1 AND X[day, pB]=1 (consecutive pair filled).
 *   Lone singles (period NOT part of any pair) are forced to 0.
 *
 * B4 strategy:
 *   Two single-period subjects sharing the same first word cannot be placed
 *   in the same slot-pair (at most one across both periods of the pair).
 */
@Component
public class BlockLayoutConstraints {

    private static final Logger log = LoggerFactory.getLogger(BlockLayoutConstraints.class);

    private final HardConstraints hardConstraints;

    public BlockLayoutConstraints(HardConstraints hardConstraints) {
        this.hardConstraints = hardConstraints;
    }

    public void addAll(CpModel model, SolverContext ctx,
                       Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        addH5B1_BlockPairing(model, ctx, vars);
        addB4_SameFirstWordPairLimit(model, ctx, vars);
        log.info("Block layout constraints (H5, B1, B4) added.");
    }

    // ─── H5 + B1: Block subjects must occupy consecutive pairs ──────────────

    private void addH5B1_BlockPairing(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int blockCount = 0;
        int forcedCount = 0;

        for (AssignmentDTO asgn : ctx.schedulable) {
            CurriculumItemDTO item = resolveCurriculum(ctx, asgn);
            if (item == null || item.maxPeriodsPerDay() < 2) continue;  // not a block subject

            Map<Integer, Map<Integer, BoolVar>> dayMap = hardConstraints.getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;

            for (int day = 1; day <= 6; day++) {
                List<int[]> pairs = ctx.slotPairsByDay.getOrDefault(day, List.of());
                Set<Integer> pairedPeriods = new HashSet<>();

                for (int[] pair : pairs) {
                    int pA = pair[0];
                    int pB = pair[1];
                    pairedPeriods.add(pA);
                    pairedPeriods.add(pB);

                    BoolVar xA = getVar(dayMap, day, pA);
                    BoolVar xB = getVar(dayMap, day, pB);
                    if (xA == null || xB == null) continue;

                    // blockVar = 1 iff xA=1 AND xB=1
                    String bvName = String.format("block_%s_%s_d%d_p%d_%d",
                            sanitize(asgn.sectionId()), sanitize(asgn.subjectName()), day, pA, pB);
                    BoolVar blockVar = model.newBoolVar(bvName);

                    // blockVar=1 => xA=1 AND xB=1
                    model.addImplication(blockVar, xA);
                    model.addImplication(blockVar, xB);

                    // xA=1 AND xB=1 => blockVar=1  (i.e. NOT blockVar => NOT xA OR NOT xB)
                    model.addBoolOr(new Literal[]{blockVar, xA.not(), xB.not()});
                    blockCount++;
                }

                // Force lone singles (not in any pair) to 0 — H5
                final int dayFinal = day;
                List<Integer> dayPeriods = ctx.allSlots.stream()
                        .filter(s -> s.dayIndex() == dayFinal)
                        .map(TimeSlotDTO::period)
                        .collect(Collectors.toList());
                for (int period : dayPeriods) {
                    if (!pairedPeriods.contains(period)) {
                        BoolVar v = getVar(dayMap, day, period);
                        if (v != null) {
                            model.addEquality(v, 0);
                            forcedCount++;
                        }
                    }
                }
            }
        }
        log.debug("H5/B1: created {} block indicator vars, forced {} lone singles to 0", blockCount, forcedCount);
    }

    // ─── B4: Same-first-word single-period subjects cannot share a slot pair ─

    private void addB4_SameFirstWordPairLimit(
            CpModel model, SolverContext ctx,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        int count = 0;

        for (SectionDTO sec : ctx.sections) {
            String secId = sec.id();
            // Only single-period subjects (maxPeriodsPerDay == 1) for this section
            Map<String, List<AssignmentDTO>> groups = new HashMap<>();
            for (AssignmentDTO a : ctx.schedulable) {
                if (!a.sectionId().equals(secId)) continue;
                CurriculumItemDTO item = resolveCurriculum(ctx, a);
                if (item == null || item.maxPeriodsPerDay() > 1) continue;
                String fw = firstWord(a.subjectName());
                groups.computeIfAbsent(fw, k -> new ArrayList<>()).add(a);
            }

            for (Map.Entry<String, List<AssignmentDTO>> entry : groups.entrySet()) {
                List<AssignmentDTO> singles = entry.getValue();
                if (singles.size() < 2) continue;

                for (int day = 1; day <= 6; day++) {
                    List<int[]> pairs = ctx.slotPairsByDay.getOrDefault(day, List.of());
                    for (int[] pair : pairs) {
                        List<BoolVar> pairVars = new ArrayList<>();
                        for (AssignmentDTO a : singles) {
                            Map<Integer, Map<Integer, BoolVar>> dm = hardConstraints.getAsgnDayMap(vars, a);
                            if (dm == null) continue;
                            BoolVar vA = getVar(dm, day, pair[0]);
                            BoolVar vB = getVar(dm, day, pair[1]);
                            if (vA != null) pairVars.add(vA);
                            if (vB != null) pairVars.add(vB);
                        }
                        if (pairVars.size() > 1) {
                            model.addLinearConstraint(
                                    LinearExpr.sum(pairVars.toArray(new BoolVar[0])), 0, 1);
                            count++;
                        }
                    }
                }
            }
        }
        log.debug("B4: added {} same-first-word pair constraints", count);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private BoolVar getVar(Map<Integer, Map<Integer, BoolVar>> dayMap, int day, int period) {
        Map<Integer, BoolVar> pm = dayMap.get(day);
        return pm == null ? null : pm.get(period);
    }

    private CurriculumItemDTO resolveCurriculum(SolverContext ctx, AssignmentDTO asgn) {
        for (SectionDTO sec : ctx.sections) {
            if (sec.id().equals(asgn.sectionId())) {
                List<CurriculumItemDTO> items = ctx.curriculumByKey != null
                        ? ctx.curriculumByKey.get(sec.curriculumKey()) : null;
                if (items == null) return new CurriculumItemDTO(asgn.subjectName(), asgn.periodsPerWeek(), 2, "Subject");
                for (CurriculumItemDTO item : items) {
                    if (item.subjectName().equalsIgnoreCase(asgn.subjectName())
                            || normalize(item.subjectName()).contains(normalize(asgn.subjectName()))
                            || normalize(asgn.subjectName()).contains(normalize(item.subjectName()))) {
                        return item;
                    }
                }
                return new CurriculumItemDTO(asgn.subjectName(), asgn.periodsPerWeek(), 2, "Subject");
            }
        }
        return null;
    }

    private String firstWord(String subject) {
        if (subject == null || subject.isBlank()) return "";
        return subject.trim().split("\\s+")[0].toLowerCase();
    }

    private String sanitize(String s) {
        if (s == null) return "null";
        return s.replaceAll("[^a-zA-Z0-9]", "_").substring(0, Math.min(s.length(), 15));
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }
}
