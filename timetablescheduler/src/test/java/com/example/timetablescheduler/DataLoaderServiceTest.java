package com.example.timetablescheduler;

import com.example.timetablescheduler.loader.DataLoaderService;
import com.example.timetablescheduler.data.SolverContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataLoaderServiceTest {

    @Autowired
    private DataLoaderService dataLoader;

    @Autowired
    private SolverContext ctx;

    @Test
    public void testLoadAllData() throws Exception {
        DataLoaderService.LoadSummary summary = dataLoader.loadAll();

        assertNotNull(summary);
        assertEquals(41, summary.sections(), "Should load 41 sections");
        // Schedulable demand is curriculum-driven: one item per curriculum subject across the
        // 30 non-Pre-Primary sections (Pre-Primary excluded per rule P4).
        assertEquals(358, summary.schedulableAssignments(), "Should have 358 curriculum-driven schedulable items");
        assertEquals(92, summary.unassignedRows(), "Should have 92 unassigned placeholder rows");
        assertEquals(142, summary.zeroWorkloadRows(), "Should have 142 zero-workload rows");
        assertEquals(44, summary.teachers(), "Should have 44 teachers");

        assertTrue(ctx.allSlots.size() > 0, "Time slots should be populated");
        assertEquals(52, ctx.allSlots.size(), "Should have 52 valid teaching slots per week");

        assertNotNull(ctx.curriculumByKey, "Curriculum should be loaded");
        assertFalse(ctx.dataConflicts.isEmpty(), "Data conflicts should be detected (UNASSIGNED + zero workload)");

        System.out.println("DataLoaderService verified successfully!");
        System.out.println("Sections: " + summary.sections());
        System.out.println("Schedulable assignments: " + summary.schedulableAssignments());
        System.out.println("Data conflicts: " + summary.dataConflicts());
    }
}

