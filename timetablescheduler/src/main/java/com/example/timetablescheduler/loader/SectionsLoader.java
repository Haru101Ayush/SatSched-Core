package com.example.timetablescheduler.loader;

import com.example.timetablescheduler.data.*;
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
 * Parses sections.json -> List<SectionDTO>.
 */
@Service
public class SectionsLoader {

    private static final Logger log = LoggerFactory.getLogger(SectionsLoader.class);
    private final ObjectMapper mapper;

    @Value("${data.sections.path:datasets/sections.json}")
    private String sectionsPath;

    public SectionsLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<SectionDTO> load(String externalPath) throws Exception {
        InputStream is = resolveStream(externalPath != null ? externalPath : sectionsPath);
        JsonNode root = mapper.readTree(is);
        JsonNode arr = root != null ? root.get("sections") : null;
        if (arr == null || !arr.isArray()) {
            throw new IllegalArgumentException("sections.json is invalid: expected a 'sections' array.");
        }
        List<SectionDTO> list = new ArrayList<>();
        int skipped = 0;
        for (JsonNode n : arr) {
            String id = n.path("id").asText("").trim();
            if (id.isEmpty()) { skipped++; continue; } // skip rows with no id rather than emit a broken section
            list.add(new SectionDTO(
                    id,
                    n.path("displayName").asText(id),
                    n.path("grade").asText(""),
                    n.path("section").asText(""),
                    n.path("curriculumKey").asText(""),
                    n.path("capacity").asInt(35)
            ));
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("sections.json contained no valid sections (all rows missing 'id').");
        }
        if (skipped > 0) log.warn("Skipped {} section rows with no 'id'", skipped);
        log.info("Loaded {} sections", list.size());
        return list;
    }

    private InputStream resolveStream(String path) throws Exception {
        // Try classpath first, then filesystem
        try {
            ClassPathResource r = new ClassPathResource(path);
            if (r.exists()) return r.getInputStream();
        } catch (Exception ignored) {}
        java.io.File f = new java.io.File(path);
        if (f.exists()) return new java.io.FileInputStream(f);
        throw new IllegalArgumentException("Cannot find sections resource: " + path);
    }
}

