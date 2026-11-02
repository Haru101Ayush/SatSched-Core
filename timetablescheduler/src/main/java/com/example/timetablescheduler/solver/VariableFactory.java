package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Creates and stores the BoolVar decision variable matrix.
 *
 * Variable design:
 *   X[sectionId, subjectName, teacherCode, dayIndex, period] in {0, 1}
 *   1 = this (section, subject, teacher) is scheduled at (day, period)
 *   0 = it is not
 *
 * Teacher is FIXED per (section, subject) pair from assignment data (T2).
 * The solver only decides WHEN (d, p) — not WHO teaches.
 *
 * Variables are NOT created for:
 *   - Break slots (H4)
 *   - UNASSIGNED-TT rows (DATA-1)
 *   - Zero-workload rows (H10)
 */
@Component
public class VariableFactory {

    private static final Logger log = LoggerFactory.getLogger(VariableFactory.class);

    /**
     * 5-level nested map for O(1) lookup:
     *   section -> subject -> teacher -> day -> period -> BoolVar
     */
    private Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars;

    public Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> createVariables(
            CpModel model,
            List<AssignmentDTO> schedulable,
            List<TimeSlotDTO> allSlots) {

        vars = new HashMap<>();
        int count = 0;

        for (AssignmentDTO asgn : schedulable) {
            String sec = asgn.sectionId();
            String sub = asgn.subjectName();
            String tch = asgn.teacherId();

            vars.computeIfAbsent(sec, k -> new HashMap<>())
                .computeIfAbsent(sub, k -> new HashMap<>())
                .computeIfAbsent(tch, k -> new HashMap<>());

            for (TimeSlotDTO slot : allSlots) {
                int d = slot.dayIndex();
                int p = slot.period();

                String varName = String.format("x_%s_%s_%s_d%d_p%d",
                        sanitize(sec), sanitize(sub), sanitize(tch), d, p);

                BoolVar v = model.newBoolVar(varName);

                vars.get(sec).get(sub).get(tch)
                    .computeIfAbsent(d, k -> new HashMap<>())
                    .put(p, v);
                count++;
            }
        }

        log.info("Created {} BoolVar decision variables", count);
        return vars;
    }

    /** Returns BoolVar or null if not found. */
    public BoolVar getVar(String sectionId, String subject, String teacherCode, int day, int period) {
        try {
            return vars.get(sectionId).get(subject).get(teacherCode).get(day).get(period);
        } catch (NullPointerException e) {
            return null;
        }
    }

    public Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> getVars() {
        return vars;
    }

    private String sanitize(String s) {
        if (s == null) return "null";
        return s.replaceAll("[^a-zA-Z0-9]", "_").substring(0, Math.min(s.length(), 20));
    }
}

