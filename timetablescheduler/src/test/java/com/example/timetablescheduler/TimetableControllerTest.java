package com.example.timetablescheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFullWorkflow() throws Exception {
        // 1. Load data
        mockMvc.perform(post("/api/timetable/load-data")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.summary.sections").value(41))
                .andExpect(jsonPath("$.summary.teachers").value(44))
                .andExpect(jsonPath("$.summary.unassignedRows").value(92))
                .andExpect(jsonPath("$.summary.zeroWorkloadRows").value(142));

        // 2. Check sections-list
        mockMvc.perform(get("/api/timetable/sections-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(41));

        // 3. Check teachers-list
        mockMvc.perform(get("/api/timetable/teachers-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(44));

        // 4. Check conflicts before generate
        mockMvc.perform(get("/api/timetable/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataConflicts").isArray());

        // 5. Generate timetable
        mockMvc.perform(post("/api/timetable/generate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solver.status").isNotEmpty());

        // 6. Check status endpoint
        mockMvc.perform(get("/api/timetable/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataLoaded").value(true));
    }
}

