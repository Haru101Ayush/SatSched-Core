package com.example.timetablescheduler;

import com.example.timetablescheduler.loader.BellScheduleLoader;
import com.example.timetablescheduler.loader.CurriculumLoader;
import com.example.timetablescheduler.loader.SectionsLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Confirms loaders reject malformed input with a clear message instead of crashing (NPE). */
@SpringBootTest
public class ValidationGuardsTest {

    @Autowired SectionsLoader sectionsLoader;
    @Autowired BellScheduleLoader bellLoader;
    @Autowired CurriculumLoader curriculumLoader;

    private Path tmp(String name, String content) throws Exception {
        Path p = Files.createTempFile(name, ".tmp");
        Files.writeString(p, content);
        p.toFile().deleteOnExit();
        return p;
    }

    @Test
    public void sectionsLoaderRejectsMissingArray() throws Exception {
        Path bad = tmp("sections", "{}");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sectionsLoader.load(bad.toString()));
        assertTrue(ex.getMessage().contains("sections"));
    }

    @Test
    public void bellLoaderRejectsMissingPeriods() throws Exception {
        Path bad = tmp("bell", "{\"mondayToThursday\":{}}");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bellLoader.load(bad.toString()));
        assertTrue(ex.getMessage().contains("bell-schedule"));
    }

    @Test
    public void curriculumLoaderRejectsEmptyFile() throws Exception {
        Path bad = tmp("curriculum", "# nothing parseable here\n");
        assertThrows(IllegalArgumentException.class,
                () -> curriculumLoader.load(bad.toString()));
    }
}
