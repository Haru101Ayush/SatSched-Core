package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adds the PR-MATH soft objective to the CpModel.
 * Prefer Mathematics in morning periods (1–4 on Mon–Thu).
 * Penalty = number of Math periods scheduled OUTSIDE morning slots.
 */
@Component
public class SoftObjective {

    private static final Logger log = LoggerFactory.getLogger(SoftObjective.class);

    private static final Set<Integer> MORNING_PERIODS = Set.of(1, 2, 3, 4);
    private static final Set<Integer> MORNING_DAYS = Set.of(1, 2, 3, 4); // Mon-Thu

    private final HardConstraints hardConstraints;

    public SoftObjective(HardConstraints hardConstraints) {
        this.hardConstraints = hardConstraints;
    }

    public void addObjective(CpModel model, SolverContext ctx,
                             Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars) {

        List<BoolVar> mathNotMorning = new ArrayList<>();

        for (AssignmentDTO asgn : ctx.schedulable) {
            if (!asgn.subjectName().equalsIgnoreCase("Mathematics")) continue;
            Map<Integer, Map<Integer, BoolVar>> dayMap = hardConstraints.getAsgnDayMap(vars, asgn);
            if (dayMap == null) continue;

            for (TimeSlotDTO slot : ctx.allSlots) {
                boolean isMorning = MORNING_DAYS.contains(slot.dayIndex())
                        && MORNING_PERIODS.contains(slot.period());
                if (isMorning) continue;  // morning slot — no penalty

                BoolVar v = getVar(dayMap, slot.dayIndex(), slot.period());
                if (v != null) mathNotMorning.add(v);
            }
        }

        if (!mathNotMorning.isEmpty()) {
            model.minimize(LinearExpr.sum(mathNotMorning.toArray(new BoolVar[0])));
            log.info("PR-MATH soft objective: minimize {} non-morning Math period vars", mathNotMorning.size());
        } else {
            log.info("PR-MATH: no Mathematics assignments found, skipping objective.");
        }
    }

    private BoolVar getVar(Map<Integer, Map<Integer, BoolVar>> dayMap, int day, int period) {
        Map<Integer, BoolVar> pm = dayMap.get(day);
        return pm == null ? null : pm.get(period);
    }
}

