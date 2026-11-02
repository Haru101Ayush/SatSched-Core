package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.TimeSlotDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Parses bell-schedule.json and produces:
 *   - allSlots: all valid teaching TimeSlotDTOs (breaks excluded — H4)
 *   - slotPairsByDay: consecutive non-break period pairs per dayIndex
 */
@Service
public class BellScheduleLoader {

    private static final Logger log = LoggerFactory.getLogger(BellScheduleLoader.class);

    // Days 1-4 = Mon-Thu, Days 5-6 = Fri-Sat
    private static final Set<Integer> MON_TO_THU = Set.of(1, 2, 3, 4);
    private static final String[] DAY_NAMES = {"", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};

    @Value("${data.bellschedule.path:datasets/bell-schedule.json}")
    private String bellPath;

    private final ObjectMapper mapper;

    public BellScheduleLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Result load(String externalPath) throws Exception {
        InputStream is = resolveStream(externalPath != null ? externalPath : bellPath);
        JsonNode root = mapper.readTree(is);

        List<TimeSlotDTO> allSlots = new ArrayList<>();
        Map<Integer, List<int[]>> slotPairsByDay = new LinkedHashMap<>();

        // Mon-Thu: days 1-4
        JsonNode monThu = root != null ? root.get("mondayToThursday") : null;
        requirePeriods(monThu, "mondayToThursday");
        List<int[]> monThuPairs = buildPairs(monThu.get("teachingPeriods"));
        for (int day = 1; day <= 4; day++) {
            List<TimeSlotDTO> daySlots = buildSlots(monThu.get("teachingPeriods"), day);
            allSlots.addAll(daySlots);
            slotPairsByDay.put(day, monThuPairs);
        }

        // Fri-Sat: days 5-6
        JsonNode friSat = root.get("fridayAndSaturday");
        requirePeriods(friSat, "fridayAndSaturday");
        List<int[]> friSatPairs = buildPairs(friSat.get("teachingPeriods"));
        for (int day = 5; day <= 6; day++) {
            List<TimeSlotDTO> daySlots = buildSlots(friSat.get("teachingPeriods"), day);
            allSlots.addAll(daySlots);
            slotPairsByDay.put(day, friSatPairs);
        }

        log.info("Loaded {} teaching slots across 6 days", allSlots.size());
        return new Result(allSlots, slotPairsByDay);
    }

    private void requirePeriods(JsonNode dayNode, String name) {
        if (dayNode == null || dayNode.get("teachingPeriods") == null
                || !dayNode.get("teachingPeriods").isArray()) {
            throw new IllegalArgumentException(
                    "bell-schedule.json is invalid: missing '" + name + ".teachingPeriods' array.");
        }
    }

    private List<TimeSlotDTO> buildSlots(JsonNode periods, int dayIndex) {
        List<TimeSlotDTO> slots = new ArrayList<>();
        String dayName = DAY_NAMES[dayIndex];
        for (JsonNode p : periods) {
            slots.add(new TimeSlotDTO(
                    dayName,
                    dayIndex,
                    p.path("period").asInt(),
                    true,
                    p.path("slot").asText()
            ));
        }
        return slots;
    }

    /**
     * Builds consecutive period pairs from the teaching periods list.
     * Pairs are (1,2), (3,4), (5,6), (7,8) [, (9,10)] — periods that are
     * adjacent in the same slot-pair group (same slot name prefix).
     */
    private List<int[]> buildPairs(JsonNode periods) {
        // Group periods by slot prefix (e.g. "1A"/"1B" -> pair group "1")
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (JsonNode p : periods) {
            String slot = p.path("slot").asText(); // e.g. "1A", "1B"
            // prefix is everything except last char
            String prefix = slot.length() > 1 ? slot.substring(0, slot.length() - 1) : slot;
            groups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(p.path("period").asInt());
        }
        List<int[]> pairs = new ArrayList<>();
        for (List<Integer> ps : groups.values()) {
            if (ps.size() == 2) {
                pairs.add(new int[]{ps.get(0), ps.get(1)});
            }
        }
        return pairs;
    }

    private InputStream resolveStream(String path) throws Exception {
        try {
            ClassPathResource r = new ClassPathResource(path);
            if (r.exists()) return r.getInputStream();
        } catch (Exception ignored) {}
        java.io.File f = new java.io.File(path);
        if (f.exists()) return new java.io.FileInputStream(f);
        throw new IllegalArgumentException("Cannot find bell-schedule resource: " + path);
    }

    public record Result(List<TimeSlotDTO> allSlots, Map<Integer, List<int[]>> slotPairsByDay) {}
}

