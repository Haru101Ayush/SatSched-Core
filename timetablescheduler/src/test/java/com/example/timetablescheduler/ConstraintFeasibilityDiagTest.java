package com.example.timetablescheduler;

import com.example.timetablescheduler.data.*;
import com.example.timetablescheduler.loader.DataLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

@SpringBootTest
public class ConstraintFeasibilityDiagTest {

    @Autowired
    private DataLoaderService dataLoader;

    @Autowired
    private SolverContext ctx;

    @Test
    public void diagnoseWorkloads() throws Exception {
        dataLoader.loadAll();

        Map<String, Integer> teacherWorkload = new HashMap<>();
        Map<String, Integer> teacherNonActivityWorkload = new HashMap<>();
        Map<String, Integer> sectionWorkload = new HashMap<>();

        Set<String> activities = Set.of(
            "games", "games / library", "library", "karate", "gymnastics", "skating",
            "happy feet", "band", "robotics", "abacus", "sanskrit", "french",
            "entrepreneurship", "art education"
        );

        for (AssignmentDTO a : ctx.schedulable) {
            teacherWorkload.merge(a.teacherId(), a.periodsPerWeek(), Integer::sum);
            sectionWorkload.merge(a.sectionId(), a.periodsPerWeek(), Integer::sum);

            boolean isAct = false;
            for (String act : activities) {
                if (a.subjectName().toLowerCase().contains(act)) {
                    isAct = true;
                    break;
                }
            }
            if (!isAct) {
                teacherNonActivityWorkload.merge(a.teacherId(), a.periodsPerWeek(), Integer::sum);
            }
        }

        System.out.println("=== TEACHERS WITH TOTAL WORKLOAD > 52 ===");
        teacherWorkload.forEach((t, w) -> {
            if (w > 52) {
                TeacherDTO td = ctx.teachers.get(t);
                System.out.println("Teacher " + t + " (" + (td != null ? td.name() : "?") + "): " + w + " ppw (non-activity: " + teacherNonActivityWorkload.getOrDefault(t, 0) + ")");
            }
        });

        System.out.println("=== TEACHERS WITH NON-ACTIVITY WORKLOAD > 52 ===");
        teacherNonActivityWorkload.forEach((t, w) -> {
            if (w > 52) {
                TeacherDTO td = ctx.teachers.get(t);
                System.out.println("Teacher " + t + " (" + (td != null ? td.name() : "?") + "): " + w + " ppw");
            }
        });

        System.out.println("=== SECTIONS WITH TOTAL WORKLOAD > 52 ===");
        sectionWorkload.forEach((s, w) -> {
            if (w > 52) {
                System.out.println("Section " + s + ": " + w + " ppw");
            }
        });
    }
}

