package com.example.timetablescheduler.loader;

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
 * Parses scheduling-rules.json.
 * Currently used for logging and validation — constraints are hard-coded in HardConstraints.java.
 * This loader provides rule metadata for API documentation and validation reporting.
 */
@Service
public class SchedulingRulesLoader {

    private static final Logger log = LoggerFactory.getLogger(SchedulingRulesLoader.class);

    @Value("${data.rules.path:datasets/scheduling-rules.json}")
    private String rulesPath;

    private final ObjectMapper mapper;

    public SchedulingRulesLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, String>> loadMandatoryHardRules(String externalPath) throws Exception {
        InputStream is = resolveStream(externalPath != null ? externalPath : rulesPath);
        JsonNode root = mapper.readTree(is);
        List<Map<String, String>> rules = new ArrayList<>();

        JsonNode mandatory = root.get("mandatoryHard");
        if (mandatory != null) {
            for (JsonNode rule : mandatory) {
                Map<String, String> r = new LinkedHashMap<>();
                r.put("id", rule.path("id").asText());
                r.put("code", rule.path("code").asText());
                r.put("description", rule.path("description").asText());
                r.put("cpSatHint", rule.path("cpSatHint").asText(""));
                rules.add(r);
            }
        }

        log.info("Loaded {} mandatory hard rules from scheduling-rules.json", rules.size());
        return rules;
    }

    private InputStream resolveStream(String path) throws Exception {
        try {
            ClassPathResource r = new ClassPathResource(path);
            if (r.exists()) return r.getInputStream();
        } catch (Exception ignored) {}
        java.io.File f = new java.io.File(path);
        if (f.exists()) return new java.io.FileInputStream(f);
        throw new IllegalArgumentException("Cannot find scheduling-rules resource: " + path);
    }
}

