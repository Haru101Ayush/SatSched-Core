package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rebuilds {@link SolverContext#schedulable} from the authoritative curriculum
 * (CLASS_WISE_SUBJECTS.md = exactly 52 ppw/section) rather than the raw, over-subscribed
 * Excel rows. Each curriculum item becomes one schedulable assignment whose weekly quota
 * is the curriculum value; the teacher is resolved from the Excel assignments.
 *
 * Why: the generated Excel over-subscribes sections (65–75 ppw) and assigns single teachers
 * school-wide (134 ppw), which is structurally infeasible. Curriculum-driven demand keeps
 * every section at exactly 52 ppw and keeps H3/H8 consistent.
 *
 * Gap handling (reported in dataConflicts, never silently dropped):
 *   - Section with no curriculum (Pre-Primary) -> excluded per rule P4.
 *   - Curriculum subject with no formally assigned teacher -> scheduled with an exempt
 *     "UNASSIGNED-TT" placeholder so the grid still fills; reported NO_FORMAL_TEACHER.
 *   - Real teacher already at 52 ppw capacity -> overflow subject uses the placeholder;
 *     reported TEACHER_OVER_CAPACITY.
 * Activity groups (whole-school, instructor not teacher-booked per A3) use an exempt
 * "ACTIVITY" placeholder and never consume teacher capacity.
 */
@Service
public class CurriculumReconciler {

    private static final Logger log = LoggerFactory.getLogger(CurriculumReconciler.class);

    private static final int TEACHER_WEEKLY_CAPACITY = 52;
    private static final int TEACHING_DAYS = 6;
    private static final String UNASSIGNED = "UNASSIGNED-TT";
    private static final String ACTIVITY = "ACTIVITY";

    private static final Set<String> ACTIVITY_KEYWORDS = Set.of(
            "games", "library", "karate", "gymnastics", "skating", "happy feet", "band",
            "robotics", "abacus", "sanskrit", "french", "entrepreneurship");

    public void reconcile(SolverContext ctx) {
        normalizeCurriculumFeasibility(ctx);

        // Index positive, formally-assigned Excel rows by section.
        Map<String, List<AssignmentDTO>> rawBySection = new HashMap<>();
        for (AssignmentDTO a : ctx.assignments) {
            if (a.isUnassigned() || a.isZeroWorkload()) continue;
            rawBySection.computeIfAbsent(a.sectionId(), k -> new ArrayList<>()).add(a);
        }

        Map<String, Integer> teacherRemaining = new HashMap<>();
        List<AssignmentDTO> schedulable = new ArrayList<>();
        List<ConflictDTO> conflicts = new ArrayList<>();

        for (SectionDTO sec : ctx.sections) {
            List<CurriculumItemDTO> curriculum =
                    ctx.curriculumByKey != null ? ctx.curriculumByKey.get(sec.curriculumKey()) : null;
            if (curriculum == null || curriculum.isEmpty()) {
                conflicts.add(new ConflictDTO("NO_CURRICULUM", sec.displayName(), "", "",
                        "No curriculum defined for '" + sec.curriculumKey()
                                + "' — Pre-Primary excluded from scheduling (rule P4)."));
                continue;
            }

            List<AssignmentDTO> raw = rawBySection.getOrDefault(sec.id(), List.of());
            for (CurriculumItemDTO item : curriculum) {
                if (isActivity(item)) {
                    schedulable.add(assignment(sec, item, ACTIVITY, "Activity"));
                    continue;
                }
                AssignmentDTO match = resolveTeacher(raw, item.subjectName());
                if (match == null) {
                    schedulable.add(assignment(sec, item, UNASSIGNED, "Unassigned"));
                    conflicts.add(new ConflictDTO("NO_FORMAL_TEACHER", sec.displayName(),
                            item.subjectName(), "",
                            "No teacher assigned in Excel for this curriculum subject — slot left Unassigned."));
                    continue;
                }
                int remaining = teacherRemaining.getOrDefault(match.teacherId(), TEACHER_WEEKLY_CAPACITY);
                if (remaining >= item.periodsPerWeek()) {
                    teacherRemaining.put(match.teacherId(), remaining - item.periodsPerWeek());
                    schedulable.add(assignment(sec, item, match.teacherId(), match.teacherName()));
                } else {
                    schedulable.add(assignment(sec, item, UNASSIGNED, "Unassigned"));
                    conflicts.add(new ConflictDTO("TEACHER_OVER_CAPACITY", sec.displayName(),
                            item.subjectName(), match.teacherId(),
                            "Teacher " + match.teacherId() + " exceeds 52 ppw capacity — slot left Unassigned."));
                }
            }
        }

        ctx.schedulable = schedulable;
        ctx.dataConflicts.addAll(conflicts);
        log.info("Reconciled to curriculum: {} schedulable items, {} reconciliation conflicts "
                + "(NO_FORMAL_TEACHER/TEACHER_OVER_CAPACITY/NO_CURRICULUM)",
                schedulable.size(), conflicts.size());
    }

    /**
     * Raises curriculum maxPeriodsPerDay only where the weekly load of same-first-word
     * subjects cannot fit in 6 days under L1 (those subjects may not share a day), e.g.
     * Class 11/12 English Literature (7 ppw) + English Language (5 ppw) needs 7 day-slots
     * at max 2/day but only 6 days exist.
     */
    private void normalizeCurriculumFeasibility(SolverContext ctx) {
        if (ctx.curriculumByKey == null) return;
        for (var entry : ctx.curriculumByKey.entrySet()) {
            List<CurriculumItemDTO> items = new ArrayList<>(entry.getValue());
            Map<String, List<Integer>> groups = new HashMap<>(); // firstWord -> indices
            for (int i = 0; i < items.size(); i++) {
                groups.computeIfAbsent(firstWord(items.get(i).subjectName()), k -> new ArrayList<>()).add(i);
            }
            boolean changed = false;
            // (a) Same-first-word subjects may not share a day (L1): the group must fit in 6 days.
            for (List<Integer> idxs : groups.values()) {
                int guard = 0;
                while (daysNeeded(items, idxs) > TEACHING_DAYS && guard++ < 100) {
                    int worst = idxs.get(0);
                    for (int i : idxs) if (daysForItem(items.get(i)) > daysForItem(items.get(worst))) worst = i;
                    items.set(worst, bumpMaxDay(items.get(worst)));
                    changed = true;
                }
            }
            // (b) Keep one day of slack per subject so dense sections (Class 11/12 commerce
            //     subjects at 2/day) stay feasible once layout rules are applied.
            for (int i = 0; i < items.size(); i++) {
                int guard = 0;
                while (daysForItem(items.get(i)) > TEACHING_DAYS - 1 && guard++ < 100) {
                    items.set(i, bumpMaxDay(items.get(i)));
                    changed = true;
                }
            }
            if (changed) {
                entry.setValue(items);
                log.info("Relaxed maxPeriodsPerDay for '{}' to fit same-first-word subjects into {} days",
                        entry.getKey(), TEACHING_DAYS);
            }
        }
    }

    private int daysNeeded(List<CurriculumItemDTO> items, List<Integer> idxs) {
        int sum = 0;
        for (int i : idxs) sum += daysForItem(items.get(i));
        return sum;
    }

    private CurriculumItemDTO bumpMaxDay(CurriculumItemDTO it) {
        return new CurriculumItemDTO(it.subjectName(), it.periodsPerWeek(), it.maxPeriodsPerDay() + 1, it.type());
    }

    private int daysForItem(CurriculumItemDTO item) {
        int max = Math.max(1, item.maxPeriodsPerDay());
        return (int) Math.ceil((double) item.periodsPerWeek() / max);
    }

    private AssignmentDTO assignment(SectionDTO sec, CurriculumItemDTO item, String teacherId, String teacherName) {
        return new AssignmentDTO(sec.id(), item.subjectName(), teacherId, teacherName,
                item.periodsPerWeek(), false, false);
    }

    private AssignmentDTO resolveTeacher(List<AssignmentDTO> raw, String subjectName) {
        String target = norm(subjectName);
        for (AssignmentDTO a : raw) {
            String s = norm(a.subjectName());
            if (s.equals(target) || s.contains(target) || target.contains(s)) return a;
        }
        return null;
    }

    private boolean isActivity(CurriculumItemDTO item) {
        if ("Activity Group".equalsIgnoreCase(item.type())) return true;
        String s = item.subjectName().toLowerCase();
        for (String kw : ACTIVITY_KEYWORDS) if (s.contains(kw)) return true;
        return false;
    }

    private String firstWord(String subject) {
        if (subject == null || subject.isBlank()) return "";
        return subject.trim().split("\\s+")[0].toLowerCase();
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ").trim();
    }
}
