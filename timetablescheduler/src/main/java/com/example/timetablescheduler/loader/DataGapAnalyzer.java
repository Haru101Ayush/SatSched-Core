package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Analyses loaded data and produces ConflictDTO entries for all data quality issues.
 * Never silently drops any row — all gaps are surfaced in dataConflicts.
 *
 * Produces:
 *   UNASSIGNED_TEACHER  — 92 UNASSIGNED-TT rows
 *   ZERO_WORKLOAD       — 142 zero-workload rows
 *   NO_ASSIGNMENTS      — sections with no assignment data at all
 *   CURRICULUM_MISMATCH — subject in curriculum but missing from xlsx
 */
@Service
public class DataGapAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DataGapAnalyzer.class);

    public List<ConflictDTO> analyze(
            List<SectionDTO> sections,
            List<AssignmentDTO> allAssignments,
            Map<String, List<CurriculumItemDTO>> curriculumByKey) {

        List<ConflictDTO> conflicts = new ArrayList<>();
        int unassignedCount = 0;
        int zeroCount = 0;

        // Index assignments by sectionId
        Map<String, List<AssignmentDTO>> bySec = new HashMap<>();
        for (AssignmentDTO a : allAssignments) {
            bySec.computeIfAbsent(a.sectionId(), k -> new ArrayList<>()).add(a);
        }

        for (AssignmentDTO a : allAssignments) {
            if (a.isUnassigned()) {
                conflicts.add(new ConflictDTO(
                        "UNASSIGNED_TEACHER",
                        a.sectionId(),
                        a.subjectName(),
                        a.teacherId(),
                        "Assignment row uses UNASSIGNED-TT — cannot schedule until teacher assigned."
                ));
                unassignedCount++;
            } else if (a.isZeroWorkload()) {
                conflicts.add(new ConflictDTO(
                        "ZERO_WORKLOAD",
                        a.sectionId(),
                        a.subjectName(),
                        a.teacherId(),
                        "Assignment has periodsPerWeek=0 — excluded from scheduling."
                ));
                zeroCount++;
            }
        }

        // Check for sections with no assignment data
        for (SectionDTO sec : sections) {
            if (!bySec.containsKey(sec.id())) {
                conflicts.add(new ConflictDTO(
                        "NO_ASSIGNMENTS",
                        sec.id(),
                        "",
                        "",
                        "No assignment data found for section '" + sec.displayName() + "'."
                ));
            }
        }

        // Check curriculum vs assignment coverage (only for classes with curriculum data)
        for (SectionDTO sec : sections) {
            List<CurriculumItemDTO> currItems = curriculumByKey.get(sec.curriculumKey());
            if (currItems == null) continue;  // Pre-Primary etc. skipped
            List<AssignmentDTO> secAssignments = bySec.getOrDefault(sec.id(), List.of());

            Set<String> assignedSubjects = new HashSet<>();
            for (AssignmentDTO a : secAssignments) {
                assignedSubjects.add(normalize(a.subjectName()));
            }

            for (CurriculumItemDTO item : currItems) {
                if (!assignedSubjects.contains(normalize(item.subjectName()))) {
                    conflicts.add(new ConflictDTO(
                            "CURRICULUM_MISMATCH",
                            sec.id(),
                            item.subjectName(),
                            "",
                            "Subject '" + item.subjectName() + "' in curriculum for " + sec.curriculumKey()
                                    + " but no assignment row found for section '" + sec.displayName() + "'."
                    ));
                }
            }
        }

        log.info("Data gap analysis: {} UNASSIGNED, {} ZERO_WORKLOAD, {} total conflicts",
                unassignedCount, zeroCount, conflicts.size());
        return conflicts;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }
}

