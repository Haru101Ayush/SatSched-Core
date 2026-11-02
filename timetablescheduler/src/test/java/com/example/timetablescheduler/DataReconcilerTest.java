package com.example.timetablescheduler;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class DataReconcilerTest {

    @Test
    public void inspectTeacherClassAssignments() throws Exception {
        Path p = Paths.get("../TEACHER_CLASS_ASSIGNMENTS.md");
        if (!Files.exists(p)) {
            p = Paths.get("TEACHER_CLASS_ASSIGNMENTS.md");
        }
        if (!Files.exists(p)) {
            System.out.println("File not found at " + p.toAbsolutePath());
            return;
        }

        List<String> lines = Files.readAllLines(p);
        System.out.println("Read lines: " + lines.size());

        // Parse Teacher Assignments by Class
        Pattern teacherHeader = Pattern.compile("^###\\s+`([^`]+)`\\s+—\\s+(.+)$");
        Pattern tableRow = Pattern.compile("^\\|\\s*(Class\\s+[0-9]+)\\s*\\|\\s*([^|]+)\\|\\s*([^|]+)\\|\\s*([0-9]+)\\s*\\|$");

        String currentCode = null;
        String currentTeacher = null;
        int totalRows = 0;
        int zeroRows = 0;
        int positiveRows = 0;

        Map<String, Integer> classSectionCount = new HashMap<>();
        classSectionCount.put("Class 1", 4);
        classSectionCount.put("Class 2", 4);
        classSectionCount.put("Class 3", 4);
        classSectionCount.put("Class 4", 3);
        classSectionCount.put("Class 5", 3);
        classSectionCount.put("Class 6", 2);
        classSectionCount.put("Class 7", 1);
        classSectionCount.put("Class 8", 1);
        classSectionCount.put("Class 9", 2);
        classSectionCount.put("Class 10", 2);
        classSectionCount.put("Class 11", 2);
        classSectionCount.put("Class 12", 2);

        int expandedTotal = 0;
        int expandedPositive = 0;
        int expandedZero = 0;

        for (String line : lines) {
            Matcher tm = teacherHeader.matcher(line);
            if (tm.find()) {
                currentCode = tm.group(1).trim();
                currentTeacher = tm.group(2).trim();
                continue;
            }

            Matcher rm = tableRow.matcher(line);
            if (rm.find() && currentCode != null) {
                String cls = rm.group(1).trim();
                int ppw = Integer.parseInt(rm.group(4).trim());

                int sections = classSectionCount.getOrDefault(cls, 0);
                expandedTotal += sections;
                if (ppw == 0) expandedZero += sections;
                else expandedPositive += sections;
            }
        }

        Pattern unassignedRow = Pattern.compile("^\\|\\s*(Class\\s+[0-9]+)\\s*\\|\\s*([^|]+)\\|\\s*([^|]+)\\|");
        boolean inUnassigned = false;
        int unassignedTotal = 0;
        int unassignedExpanded = 0;

        for (String line : lines) {
            if (line.contains("## 3. Unassigned Placeholder Allocations")) {
                inUnassigned = true;
                continue;
            }
            if (line.contains("## 4. Teacher Assignments by Class")) {
                inUnassigned = false;
                break;
            }
            if (inUnassigned) {
                Matcher um = unassignedRow.matcher(line);
                if (um.find()) {
                    String cls = um.group(1).trim();
                    unassignedTotal++;
                    unassignedExpanded += classSectionCount.getOrDefault(cls, 0);
                }
            }
        }
        System.out.println("Unassigned placeholder rows: table=" + unassignedTotal + ", expanded=" + unassignedExpanded);

        System.out.println("Teacher assignment class-level rows: total=" + totalRows + ", positive=" + positiveRows + ", zero=" + zeroRows);
    }
}
