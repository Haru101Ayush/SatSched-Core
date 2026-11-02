package com.example.timetablescheduler.config;

import com.google.ortools.Loader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that initialises Google OR-Tools native libraries at startup.
 * Must run before any CpModel or CpSolver is constructed.
 */
@Configuration
public class SolverConfig {

    private static final Logger log = LoggerFactory.getLogger(SolverConfig.class);

    @PostConstruct
    public void loadNativeLibraries() {
        log.info("Loading OR-Tools native libraries...");
        Loader.loadNativeLibraries();
        log.info("OR-Tools native libraries loaded successfully.");
    }
}

