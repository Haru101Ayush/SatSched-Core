package com.example.timetablescheduler.reporting;

import com.example.timetablescheduler.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds section and teacher timetable views from the solved lessons in SolverContext.
 */
@Service
public class ReportingService {

    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);

    private final SolverContext ctx;

    public ReportingService(SolverContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Returns section timetables for all sections.
     * Map: sectionDisplayName -> day -> list of lesson slots
     */
    public Map<String, Map<String, List<LessonSlot>>> getSectionTimetables() {
        Map<String, Map<String, List<LessonSlot>>> result = new LinkedHashMap<>();

        // Build section id -> displayName lookup
        Map<String, String> secDisplay = new HashMap<>();
        for (SectionDTO sec : ctx.sections) {
            secDisplay.put(sec.id(), sec.displayName());
        }

        for (ScheduledLessonDTO lesson : ctx.solution) {
            String displayName = secDisplay.getOrDefault(lesson.sectionId(), lesson.sectionId());
            result.computeIfAbsent(displayName, k -> new LinkedHashMap<>())
                  .computeIfAbsent(lesson.day(), k -> new ArrayList<>())
                  .add(new LessonSlot(lesson.period(), lesson.subjectName(),
                          lesson.teacherCode(), lesson.teacherName(), null));
        }

        // Sort by period
        for (var dayMap : result.values()) {
            for (var list : dayMap.values()) {
                list.sort(Comparator.comparingInt(LessonSlot::period));
            }
        }
        return result;
    }

    /**
     * Returns timetable for a single section by id or displayName.
     */
    public Map<String, List<LessonSlot>> getSectionTimetable(String sectionIdOrName) {
        String targetId = resolveSecId(sectionIdOrName);
        Map<String, List<LessonSlot>> result = new LinkedHashMap<>();
        for (ScheduledLessonDTO lesson : ctx.solution) {
            if (lesson.sectionId().equals(targetId)
                    || lesson.sectionId().equalsIgnoreCase(sectionIdOrName)) {
                result.computeIfAbsent(lesson.day(), k -> new ArrayList<>())
                      .add(new LessonSlot(lesson.period(), lesson.subjectName(),
                              lesson.teacherCode(), lesson.teacherName(), null));
            }
        }
        for (var list : result.values()) list.sort(Comparator.comparingInt(LessonSlot::period));
        return result;
    }

    /**
     * Returns teacher timetables for all teachers.
     * Map: teacherName -> day -> list of lesson slots (with section info)
     */
    public Map<String, Map<String, List<LessonSlot>>> getTeacherTimetables() {
        Map<String, Map<String, List<LessonSlot>>> result = new LinkedHashMap<>();
        Map<String, String> secDisplay = new HashMap<>();
        for (SectionDTO sec : ctx.sections) secDisplay.put(sec.id(), sec.displayName());

        for (ScheduledLessonDTO lesson : ctx.solution) {
            String teacherKey = lesson.teacherName().isBlank() ? lesson.teacherCode() : lesson.teacherName();
            String sectionName = secDisplay.getOrDefault(lesson.sectionId(), lesson.sectionId());
            result.computeIfAbsent(teacherKey, k -> new LinkedHashMap<>())
                  .computeIfAbsent(lesson.day(), k -> new ArrayList<>())
                  .add(new LessonSlot(lesson.period(), lesson.subjectName(),
                          lesson.teacherCode(), lesson.teacherName(), sectionName));
        }
        for (var dayMap : result.values()) {
            for (var list : dayMap.values()) list.sort(Comparator.comparingInt(LessonSlot::period));
        }
        return result;
    }

    /**
     * Returns timetable for a single teacher by code or name.
     */
    public Map<String, List<LessonSlot>> getTeacherTimetable(String teacherCodeOrName) {
        Map<String, String> secDisplay = new HashMap<>();
        for (SectionDTO sec : ctx.sections) secDisplay.put(sec.id(), sec.displayName());

        Map<String, List<LessonSlot>> result = new LinkedHashMap<>();
        for (ScheduledLessonDTO lesson : ctx.solution) {
            if (lesson.teacherCode().equalsIgnoreCase(teacherCodeOrName)
                    || lesson.teacherName().equalsIgnoreCase(teacherCodeOrName)) {
                String sectionName = secDisplay.getOrDefault(lesson.sectionId(), lesson.sectionId());
                result.computeIfAbsent(lesson.day(), k -> new ArrayList<>())
                      .add(new LessonSlot(lesson.period(), lesson.subjectName(),
                              lesson.teacherCode(), lesson.teacherName(), sectionName));
            }
        }
        for (var list : result.values()) list.sort(Comparator.comparingInt(LessonSlot::period));
        return result;
    }

    private String resolveSecId(String nameOrId) {
        for (SectionDTO sec : ctx.sections) {
            if (sec.id().equalsIgnoreCase(nameOrId)
                    || sec.displayName().equalsIgnoreCase(nameOrId)) {
                return sec.id();
            }
        }
        return nameOrId;
    }

    /** A single cell in a timetable grid. */
    public record LessonSlot(int period, String subject, String teacherCode,
                              String teacherName, String section) {}
}

