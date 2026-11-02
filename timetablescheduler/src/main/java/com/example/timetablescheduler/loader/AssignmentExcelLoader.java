package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.AssignmentDTO;
import com.example.timetablescheduler.data.SectionDTO;
import com.example.timetablescheduler.data.TeacherDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Parses TEACHER_CLASS_ALLOCATION.xlsx into List<AssignmentDTO> and Map<code,TeacherDTO>.
 *
 * Expected columns (flexible header matching):
 *   Section Name | Subject Name | Teacher Code | Teacher Name | Periods Per Week
 *
 * Handles UNASSIGNED-TT and zero-workload rows per DATA-1 / H10.
 */
@Service
public class AssignmentExcelLoader {

    private static final Logger log = LoggerFactory.getLogger(AssignmentExcelLoader.class);

    @Value("${data.excel.path:TEACHER_CLASS_ALLOCATION.xlsx}")
    private String excelPath;

    public Result load(String externalPath, List<SectionDTO> sections) throws Exception {
        InputStream is = resolveStream(externalPath != null ? externalPath : excelPath);

        // Build section lookup: normalized displayName -> SectionDTO
        Map<String, SectionDTO> sectionByName = new LinkedHashMap<>();
        for (SectionDTO s : sections) {
            sectionByName.put(normalize(s.displayName()), s);
            // Also index by grade+section shorthand
            sectionByName.put(normalize(s.grade() + " " + s.section()), s);
            sectionByName.put(normalize(s.id()), s);
        }

        Workbook wb = new XSSFWorkbook(is);
        if (wb.getNumberOfSheets() == 0) {
            wb.close();
            throw new IllegalArgumentException("TEACHER_CLASS_ALLOCATION.xlsx has no sheets.");
        }
        Sheet sheet = findSheet(wb);
        if (sheet == null) {
            // Try first sheet
            sheet = wb.getSheetAt(0);
        }

        // Find header row and column indices
        int[] colIdx = findColumns(sheet);
        int sectionCol = colIdx[0];
        int subjectCol = colIdx[1];
        int teacherCodeCol = colIdx[2];
        int teacherNameCol = colIdx[3];
        int periodsCol = colIdx[4];

        List<AssignmentDTO> assignments = new ArrayList<>();
        Map<String, TeacherDTO> teachers = new LinkedHashMap<>();

        int headerRow = colIdx[5];
        DataFormatter fmt = new DataFormatter();

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String sectionName = getCellStr(row, sectionCol, fmt).trim();
            String subjectName = getCellStr(row, subjectCol, fmt).trim();
            String teacherCode = getCellStr(row, teacherCodeCol, fmt).trim();
            String teacherName = getCellStr(row, teacherNameCol, fmt).trim();
            String periodsStr = getCellStr(row, periodsCol, fmt).trim();

            if (sectionName.isEmpty() && subjectName.isEmpty()) continue;

            // Parse periods
            int periods = 0;
            try {
                periods = (int) Math.round(Double.parseDouble(periodsStr));
            } catch (NumberFormatException ignored) {}

            // Resolve section ID
            SectionDTO sec = resolveSectionId(sectionName, sectionByName);
            String sectionId = sec != null ? sec.id() : normalizeToId(sectionName);

            boolean isUnassigned = teacherCode.equalsIgnoreCase("UNASSIGNED-TT")
                    || teacherCode.equalsIgnoreCase("UNASSIGNED");
            boolean isZeroWorkload = periods <= 0;

            // Register teacher
            if (!teacherCode.isEmpty() && !isUnassigned) {
                teachers.putIfAbsent(teacherCode, new TeacherDTO(teacherCode, teacherCode, teacherName));
            }

            assignments.add(new AssignmentDTO(
                    sectionId,
                    subjectName,
                    isUnassigned ? "UNASSIGNED-TT" : teacherCode,
                    teacherName,
                    periods,
                    isUnassigned,
                    isZeroWorkload
            ));
        }

        // Also read from "Teacher Roster" sheet if present to ensure all rostered teachers are loaded
        Sheet rosterSheet = wb.getSheet("Teacher Roster");
        if (rosterSheet != null) {
            for (int r = 1; r <= rosterSheet.getLastRowNum(); r++) {
                Row row = rosterSheet.getRow(r);
                if (row == null) continue;
                String code = getCellStr(row, 0, fmt).trim();
                String name = getCellStr(row, 1, fmt).trim();
                if (!code.isEmpty() && !code.equalsIgnoreCase("UNASSIGNED-TT")) {
                    teachers.putIfAbsent(code, new TeacherDTO(code, code, name.isEmpty() ? code : name));
                }
            }
        }

        wb.close();
        log.info("Loaded {} assignment rows, {} teachers", assignments.size(), teachers.size());
        return new Result(assignments, teachers);
    }

    private Sheet findSheet(Workbook wb) {
        // Try common sheet names
        for (String name : List.of("Section Assignments", "Sheet1", "Assignments", "Timetable")) {
            Sheet s = wb.getSheet(name);
            if (s != null) return s;
        }
        return null;
    }

    /**
     * Scans the first few rows to find the header row and column positions.
     * Returns [sectionCol, subjectCol, teacherCodeCol, teacherNameCol, periodsCol, headerRowIndex].
     */
    private int[] findColumns(Sheet sheet) {
        DataFormatter fmt = new DataFormatter();
        for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int[] idx = new int[]{-1, -1, -1, -1, -1, r};
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getCellStr(row, c, fmt).trim().toLowerCase();
                if (val.contains("section") && !val.contains("subject")) idx[0] = c;
                else if (val.contains("subject")) idx[1] = c;
                else if (val.contains("code") || (val.contains("teacher") && val.contains("code"))) idx[2] = c;
                else if (val.contains("name") && idx[2] >= 0) idx[3] = c;
                else if (val.contains("period") || val.contains("workload") || val.contains("ppw") || val.contains("load")) idx[4] = c;
            }
            if (idx[0] >= 0 && idx[1] >= 0) {
                // Ensure teacher code & name defaults
                if (idx[2] < 0) idx[2] = idx[1] + 1;
                if (idx[3] < 0) idx[3] = idx[2] + 1;
                if (idx[4] < 0) idx[4] = idx[3] + 1;
                return idx;
            }
        }
        // Fallback: assume standard order Section|Subject|TeacherCode|TeacherName|Periods
        return new int[]{0, 1, 2, 3, 4, 0};
    }

    private SectionDTO resolveSectionId(String rawName, Map<String, SectionDTO> lookup) {
        String norm = normalize(rawName);
        if (lookup.containsKey(norm)) return lookup.get(norm);
        // Fuzzy: try partial match
        for (Map.Entry<String, SectionDTO> e : lookup.entrySet()) {
            if (e.getKey().contains(norm) || norm.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private String normalizeToId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private String getCellStr(Row row, int col, DataFormatter fmt) {
        if (col < 0) return "";
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : fmt.formatCellValue(cell);
    }

    private InputStream resolveStream(String path) throws Exception {
        try {
            ClassPathResource r = new ClassPathResource(path);
            if (r.exists()) return r.getInputStream();
        } catch (Exception ignored) {}
        java.io.File f = new java.io.File(path);
        if (f.exists()) return new java.io.FileInputStream(f);
        throw new IllegalArgumentException("Cannot find Excel resource: " + path);
    }

    public record Result(List<AssignmentDTO> assignments, Map<String, TeacherDTO> teachers) {}
}

