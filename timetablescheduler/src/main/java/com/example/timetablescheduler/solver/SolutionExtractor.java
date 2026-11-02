package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Extracts the solver solution from the BoolVar matrix into List<ScheduledLessonDTO>.
 */
@Component
public class SolutionExtractor {

    private static final Logger log = LoggerFactory.getLogger(SolutionExtractor.class);

    private static final String[] DAY_NAMES = {"", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};

    public void extract(
            CpSolver solver,
            Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars,
            SolverContext ctx) {

        List<ScheduledLessonDTO> lessons = new ArrayList<>();

        for (var secEntry : vars.entrySet()) {
            String sectionId = secEntry.getKey();
            for (var subEntry : secEntry.getValue().entrySet()) {
                String subject = subEntry.getKey();
                for (var tchEntry : subEntry.getValue().entrySet()) {
                    String teacherCode = tchEntry.getKey();
                    for (var dayEntry : tchEntry.getValue().entrySet()) {
                        int dayIndex = dayEntry.getKey();
                        for (var periodEntry : dayEntry.getValue().entrySet()) {
                            int period = periodEntry.getKey();
                            BoolVar v = periodEntry.getValue();
                            if (solver.booleanValue(v)) {
                                TeacherDTO teacher = ctx.teachers.get(teacherCode);
                                String teacherName = teacher != null ? teacher.name() : teacherCode;
                                lessons.add(new ScheduledLessonDTO(
                                        sectionId,
                                        DAY_NAMES[dayIndex],
                                        period,
                                        subject,
                                        teacherCode,
                                        teacherName
                                ));
                            }
                        }
                    }
                }
            }
        }

        ctx.solution = lessons;
        log.info("Solution extracted: {} scheduled lessons", lessons.size());
    }
}

