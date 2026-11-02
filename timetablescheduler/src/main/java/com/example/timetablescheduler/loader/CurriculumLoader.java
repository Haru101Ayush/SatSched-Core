package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.CurriculumItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Parses CLASS_WISE_SUBJECTS.md into a Map<curriculumKey, List<CurriculumItemDTO>>.
 *
 * Strategy: line-based markdown table parser.
 * Detects "### Class N [...]" section headers and then reads the table rows.
 */
@Service
public class CurriculumLoader {

    private static final Logger log = LoggerFactory.getLogger(CurriculumLoader.class);

    // Matches "### Class 10 [C10]" or "### Class 1 [C1]"
    private static final Pattern CLASS_HEADER = Pattern.compile(
            "^###\\s+(Class\\s+\\d+(?:\\s+\\[[A-Za-z0-9]+\\])?)\\s*.*$", Pattern.CASE_INSENSITIVE);

    // Matches a markdown table row with at least 4 pipe-delimited cells
    // | Type | Subject | Periods/Day | Periods/Week |
    private static final Pattern TABLE_ROW = Pattern.compile(
            "^\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|");

    @Value("${data.curriculum.path:CLASS_WISE_SUBJECTS.md}")
    private String curriculumPath;

    public Map<String, List<CurriculumItemDTO>> load(String externalPath) throws Exception {
        InputStream is = resolveStream(externalPath != null ? externalPath : curriculumPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        Map<String, List<CurriculumItemDTO>> result = new LinkedHashMap<>();
        String currentKey = null;
        List<CurriculumItemDTO> currentList = null;
        boolean inTable = false;
        boolean headerSeen = false;

        String line;
        while ((line = reader.readLine()) != null) {
            // Check for class header
            Matcher hm = CLASS_HEADER.matcher(line);
            if (hm.matches()) {
                // Save previous
                if (currentKey != null && currentList != null) {
                    result.put(currentKey, currentList);
                }
                // New class
                String rawKey = hm.group(1).trim();
                // Normalize: "Class 10 [C10]" -> "Class 10"
                currentKey = rawKey.replaceAll("\\s*\\[.*?\\]\\s*", "").trim();
                currentList = new ArrayList<>();
                inTable = false;
                headerSeen = false;
                continue;
            }

            if (currentKey == null) continue;

            // Detect table separator line (|---|---|...)
            if (line.contains("|") && line.contains("---")) {
                inTable = true;
                headerSeen = true;
                continue;
            }

            // Parse table data rows
            if (inTable && headerSeen && line.startsWith("|")) {
                Matcher rm = TABLE_ROW.matcher(line);
                if (rm.find()) {
                    String type = rm.group(1).trim();
                    String subjectName = rm.group(2).trim();
                    int maxPerDay;
                    int perWeek;
                    try {
                        maxPerDay = Integer.parseInt(rm.group(3).trim());
                        perWeek = Integer.parseInt(rm.group(4).trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    // Skip header row indicators
                    if (type.equalsIgnoreCase("Type") || subjectName.equalsIgnoreCase("Subject / Activity")) {
                        continue;
                    }
                    if (!subjectName.isEmpty() && perWeek > 0) {
                        currentList.add(new CurriculumItemDTO(subjectName, perWeek, maxPerDay, type));
                    }
                } else if (!line.contains("---")) {
                    // End of table
                    inTable = false;
                }
            }
        }

        // Save last class
        if (currentKey != null && currentList != null) {
            result.put(currentKey, currentList);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No curriculum parsed from CLASS_WISE_SUBJECTS.md — check the file format (### Class N headers + tables).");
        }
        // Warn on totals that would make a section infeasible (weekly load must fit 52 slots).
        for (var e : result.entrySet()) {
            int total = e.getValue().stream().mapToInt(CurriculumItemDTO::periodsPerWeek).sum();
            if (total > 52) {
                log.warn("Curriculum for '{}' totals {} ppw (> 52 slots/week) — sections of this class will be infeasible",
                        e.getKey(), total);
            }
        }

        log.info("Loaded curriculum for {} class keys: {}", result.size(), result.keySet());
        return result;
    }

    private InputStream resolveStream(String path) throws Exception {
        try {
            ClassPathResource r = new ClassPathResource(path);
            if (r.exists()) return r.getInputStream();
        } catch (Exception ignored) {}
        java.io.File f = new java.io.File(path);
        if (f.exists()) return new java.io.FileInputStream(f);
        throw new IllegalArgumentException("Cannot find curriculum resource: " + path);
    }
}

