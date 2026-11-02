package com.example.timetablescheduler.solver;

import com.example.timetablescheduler.data.*;
import com.google.ortools.sat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Orchestrates the full CP-SAT constraint model build.
 * Delegates to HardConstraints, BlockLayoutConstraints, and SoftObjective.
 */
@Component
public class ConstraintBuilder {

    private static final Logger log = LoggerFactory.getLogger(ConstraintBuilder.class);

    private final VariableFactory variableFactory;
    private final HardConstraints hardConstraints;
    private final BlockLayoutConstraints blockConstraints;
    private final SoftObjective softObjective;

    public ConstraintBuilder(VariableFactory variableFactory,
                             HardConstraints hardConstraints,
                             BlockLayoutConstraints blockConstraints,
                             SoftObjective softObjective) {
        this.variableFactory = variableFactory;
        this.hardConstraints = hardConstraints;
        this.blockConstraints = blockConstraints;
        this.softObjective = softObjective;
    }

    public Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> build(
            CpModel model, SolverContext ctx) {

        log.info("Building CP-SAT model: {} schedulable assignments, {} slots",
                ctx.schedulable.size(), ctx.allSlots.size());

        // 1. Create decision variables
        var vars = variableFactory.createVariables(model, ctx.schedulable, ctx.allSlots);

        // 2. Hard constraints (Tier 1)
        hardConstraints.addAll(model, ctx, vars);

        // 3. Block/layout constraints (Tier 2)
        blockConstraints.addAll(model, ctx, vars);

        // 4. Soft objective — PR-MATH
        softObjective.addObjective(model, ctx, vars);

        log.info("CP-SAT model build complete.");
        return vars;
    }
}

