package com.example.timetablescheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Automatically opens the Swagger UI in the default browser when the Spring Boot application starts.
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Value("${server.port:8080}")
    private int port;

    @Value("${app.open-browser-on-startup:true}")
    private boolean openBrowser;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        if (!openBrowser) {
            log.info("Automatic browser opening on startup is disabled (app.open-browser-on-startup=false).");
            return;
        }

        String url = "http://localhost:" + port + "/swagger-ui.html";
        log.info("Spring Boot started! Opening Swagger UI at: {}", url);

        // 1. Try Java Desktop API first
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Throwable t) {
            log.debug("AWT Desktop browsing not available: {}", t.getMessage());
        }

        // 2. Cross-platform OS fallback
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            } else {
                log.info("Open Swagger UI manually in your browser: {}", url);
            }
        } catch (Exception e) {
            log.warn("Could not launch browser automatically: {}. Please visit: {}", e.getMessage(), url);
        }
    }
}

