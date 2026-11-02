package com.example.timetablescheduler.reporting;

import com.example.timetablescheduler.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates conflict reports from SolverContext.
 * Combines data conflicts (from load time) with scheduling conflicts (from solve time).
 */
@Service
public class ConflictReporter {

    private static final Logger log = LoggerFactory.getLogger(ConflictReporter.class);

    private final SolverContext ctx;

    public ConflictReporter(SolverContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Returns a structured report combining data quality and scheduling conflicts.
     */
    public ConflictReport getReport() {
        return new ConflictReport(
                ctx.dataConflicts != null ? ctx.dataConflicts : List.of(),
                ctx.schedulingConflicts != null ? ctx.schedulingConflicts : List.of()
        );
    }

    /**
     * Computes post-solve validation stats:
     * - Checks no teacher is double-booked (H1)
     * - Sections with scheduled lessons vs total schedulable
     */
    public ValidationReport validate() {
        List<String> violations = new ArrayList<>();

        if (ctx.solution == null || ctx.solution.isEmpty()) {
            return new ValidationReport(false, 0, List.of("No solution to validate."));
        }

        // H1: check real-teacher double-booking. Placeholder "teachers" (unassigned slots and
        // whole-school activities) are exempt from H1 by design, so exclude them here — otherwise
        // two sections sharing an Unassigned/Activity slot would be reported as a false violation.
        Set<String> exempt = Set.of("UNASSIGNED-TT", "ACTIVITY");
        Map<String, Set<String>> teacherSlots = new HashMap<>();
        for (ScheduledLessonDTO lesson : ctx.solution) {
            if (exempt.contains(lesson.teacherCode())) continue;
            String key = lesson.teacherCode() + "_" + lesson.day() + "_P" + lesson.period();
            if (!teacherSlots.computeIfAbsent(lesson.teacherCode(), k -> new HashSet<>()).add(key)) {
                violations.add("H1 VIOLATION: Teacher " + lesson.teacherCode()
                        + " double-booked on " + lesson.day() + " period " + lesson.period());
            }
        }

        // H3: one lesson per section per slot
        Map<String, Set<String>> sectionSlots = new HashMap<>();
        for (ScheduledLessonDTO lesson : ctx.solution) {
            String key = lesson.day() + "_P" + lesson.period();
            if (!sectionSlots.computeIfAbsent(lesson.sectionId(), k -> new HashSet<>()).add(key)) {
                violations.add("H3 VIOLATION: Section " + lesson.sectionId()
                        + " has multiple lessons on " + lesson.day() + " period " + lesson.period());
            }
        }

        Map<String, SectionDTO> secById = new HashMap<>();
        for (SectionDTO s : ctx.sections) secById.put(s.id(), s);

        // H8: weekly quota per (section, subject) and per-section total == scheduled demand
        Map<String, Integer> expected = new HashMap<>();
        Map<String, Integer> expectedSecTotal = new HashMap<>();
        for (AssignmentDTO a : ctx.schedulable) {
            expected.merge(a.sectionId() + "|" + a.subjectName(), a.periodsPerWeek(), Integer::sum);
            expectedSecTotal.merge(a.sectionId(), a.periodsPerWeek(), Integer::sum);
        }
        Map<String, Integer> actual = new HashMap<>();
        Map<String, Integer> actualSecTotal = new HashMap<>();
        Map<String, Integer> perSubjectDay = new HashMap<>();
        for (ScheduledLessonDTO l : ctx.solution) {
            actual.merge(l.sectionId() + "|" + l.subjectName(), 1, Integer::sum);
            actualSecTotal.merge(l.sectionId(), 1, Integer::sum);
            perSubjectDay.merge(l.sectionId() + "|" + l.subjectName() + "|" + l.day(), 1, Integer::sum);
        }
        for (var e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got != e.getValue())
                violations.add("H8 VIOLATION: " + e.getKey() + " scheduled " + got + " != required " + e.getValue());
        }
        for (var e : expectedSecTotal.entrySet()) {
            int got = actualSecTotal.getOrDefault(e.getKey(), 0);
            if (got != e.getValue())
                violations.add("H8 VIOLATION: section " + e.getKey() + " total " + got + " != required " + e.getValue());
        }

        // H7: daily subject max not exceeded
        for (var e : perSubjectDay.entrySet()) {
            String[] p = e.getKey().split("\\|", -1);
            int maxPerDay = resolveMaxPerDay(secById.get(p[0]), p[1]);
            if (maxPerDay > 0 && e.getValue() > maxPerDay)
                violations.add("H7 VIOLATION: " + p[0] + " / " + p[1] + " on " + p[2]
                        + " = " + e.getValue() + " > maxPerDay " + maxPerDay);
        }

        // G1: Games/Library must not be in periods 1-2
        for (ScheduledLessonDTO l : ctx.solution) {
            if (isGamesOrLibrary(l.subjectName()) && (l.period() == 1 || l.period() == 2))
                violations.add("G1 VIOLATION: " + l.subjectName() + " in " + l.sectionId() + " at period " + l.period());
        }

        // G2: Class 11 & 12 — Games and Library not on the same day
        Map<String, Set<String>> gamesDays = new HashMap<>();
        Map<String, Set<String>> libDays = new HashMap<>();
        for (ScheduledLessonDTO l : ctx.solution) {
            SectionDTO s = secById.get(l.sectionId());
            if (s == null || !("11".equals(s.grade()) || "12".equals(s.grade()))) continue;
            String sub = l.subjectName().toLowerCase();
            if (sub.contains("/")) continue; // skip combined Games / Library activity
            if (sub.contains("games")) gamesDays.computeIfAbsent(l.sectionId(), k -> new HashSet<>()).add(l.day());
            if (sub.contains("library")) libDays.computeIfAbsent(l.sectionId(), k -> new HashSet<>()).add(l.day());
        }
        for (var e : gamesDays.entrySet()) {
            for (String d : e.getValue())
                if (libDays.getOrDefault(e.getKey(), Set.of()).contains(d))
                    violations.add("G2 VIOLATION: " + e.getKey() + " has Games and Library both on " + d);
        }

        // L1: same-first-word subjects must not share a day
        Map<String, Map<String, Set<String>>> byDay = new HashMap<>(); // section|day -> firstWord -> subjects
        for (ScheduledLessonDTO l : ctx.solution) {
            byDay.computeIfAbsent(l.sectionId() + "|" + l.day(), k -> new HashMap<>())
                    .computeIfAbsent(firstWord(l.subjectName()), k -> new HashSet<>())
                    .add(l.subjectName());
        }
        for (var e : byDay.entrySet()) {
            for (var g : e.getValue().entrySet()) {
                if (g.getValue().size() > 1)
                    violations.add("L1 VIOLATION: " + e.getKey() + " has same-first-word subjects " + g.getValue());
            }
        }

        boolean hardSatisfied = violations.isEmpty();
        double penalty = ctx.objectiveValue;
        log.info("Validation: hardSatisfied={}, violations={}, softPenalty={}", hardSatisfied, violations.size(), penalty);
        return new ValidationReport(hardSatisfied, (int) penalty, violations);
    }

    private int resolveMaxPerDay(SectionDTO sec, String subjectName) {
        if (sec == null || ctx.curriculumByKey == null) return 0;
        List<CurriculumItemDTO> items = ctx.curriculumByKey.get(sec.curriculumKey());
        if (items == null) return 0;
        for (CurriculumItemDTO item : items) {
            String a = item.subjectName().toLowerCase().trim();
            String b = subjectName.toLowerCase().trim();
            if (a.equals(b) || a.contains(b) || b.contains(a)) return item.maxPeriodsPerDay();
        }
        return 0;
    }

    private boolean isGamesOrLibrary(String subject) {
        String s = subject.toLowerCase().trim();
        return s.equals("games") || s.equals("library") || s.equals("games / library");
    }

    private String firstWord(String subject) {
        if (subject == null || subject.isBlank()) return "";
        return subject.trim().split("\\s+")[0].toLowerCase();
    }

    public record ConflictReport(List<ConflictDTO> dataConflicts, List<String> schedulingConflicts) {}
    public record ValidationReport(boolean hardConstraintsSatisfied, int softConstraintPenalty,
                                   List<String> violatedSoftConstraints) {}
}

