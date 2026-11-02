package com.example.timetablescheduler;

import com.example.timetablescheduler.loader.DataLoaderService;
import com.example.timetablescheduler.solver.TimetableSolverService;
import com.example.timetablescheduler.data.SolverContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SolverServiceTest {

    @Autowired
    private DataLoaderService dataLoader;

    @Autowired
    private TimetableSolverService solverService;

    @Autowired
    private SolverContext ctx;

    @Test
    public void testSolve() throws Exception {
        dataLoader.loadAll();
        System.out.println("Starting solve test...");
        TimetableSolverService.SolveResult result = solverService.solve();
        System.out.println("Solver result: success=" + result.success()
                + ", status=" + result.status()
                + ", wallTimeMs=" + result.wallTimeMs()
                + ", error=" + result.errorMessage());

        assertNotNull(result.status());
        System.out.println("Scheduled lessons count: " + ctx.solution.size());
    }
}

