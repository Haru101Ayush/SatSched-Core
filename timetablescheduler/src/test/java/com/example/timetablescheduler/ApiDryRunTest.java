package com.example.timetablescheduler;

import com.example.timetablescheduler.data.ScheduledLessonDTO;
import com.example.timetablescheduler.data.SolverContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end dry run of every REST endpoint, including invalid-input scenarios
 * (calling before data is loaded, and unknown section/teacher ids).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ApiDryRunTest {

    @Autowired private MockMvc mvc;
    @Autowired private SolverContext ctx;

    private static final String B = "/api/timetable";

    @Test
    public void dryRunAllEndpoints() throws Exception {
        // ── Invalid input: calls before data is loaded ───────────────────────
        ctx.dataLoaded = false;
        mvc.perform(get(B + "/sections")).andExpect(status().isBadRequest());
        mvc.perform(get(B + "/teachers")).andExpect(status().isBadRequest());
        mvc.perform(get(B + "/conflicts")).andExpect(status().isBadRequest());
        mvc.perform(post(B + "/generate")).andExpect(status().isBadRequest());
        // /status is always safe
        mvc.perform(get(B + "/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dataLoaded").value(false));

        // ── Load data ────────────────────────────────────────────────────────
        mvc.perform(post(B + "/load-data")).andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.summary.sections").value(41))
                .andExpect(jsonPath("$.dataConflicts").isArray());

        mvc.perform(get(B + "/sections-list")).andExpect(status().isOk());
        mvc.perform(get(B + "/teachers-list")).andExpect(status().isOk());

        // ── Generate (full solve) ────────────────────────────────────────────
        mvc.perform(post(B + "/generate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.validation.hardConstraintsSatisfied").value(true))
                .andExpect(jsonPath("$.summary.sectionsScheduled").value(30))
                .andExpect(jsonPath("$.summary.totalSlotsScheduled").value(1560));

        // ── Views ────────────────────────────────────────────────────────────
        mvc.perform(get(B + "/sections")).andExpect(status().isOk());
        mvc.perform(get(B + "/teachers")).andExpect(status().isOk());
        mvc.perform(get(B + "/conflicts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dataConflicts").isArray());
        mvc.perform(get(B + "/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dataLoaded").value(true));

        // ── Valid single section / teacher ───────────────────────────────────
        mvc.perform(get(B + "/sections/class-1-lily")).andExpect(status().isOk());
        String realTeacher = ctx.solution.stream()
                .map(ScheduledLessonDTO::teacherCode)
                .filter(c -> !Set.of("UNASSIGNED-TT", "ACTIVITY").contains(c))
                .findFirst().orElseThrow();
        mvc.perform(get(B + "/teachers/" + realTeacher)).andExpect(status().isOk());

        // ── Invalid input: unknown ids ───────────────────────────────────────
        mvc.perform(get(B + "/sections/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get(B + "/teachers/NO-SUCH-TEACHER")).andExpect(status().isNotFound());
    }
}
