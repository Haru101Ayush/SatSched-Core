package com.example.timetablescheduler;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * RDPL Timetable Scheduler — Full-School Constraint-Based Timetable Solver
 *
 * Uses Google OR-Tools CP-SAT to generate valid weekly timetables for 41 school sections.
 *
 * To run: mvn spring-boot:run
 * API base: http://localhost:8080/api/timetable
 * Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * Workflow:
 *   1. POST /api/timetable/load-data  — parse all data files
 *   2. POST /api/timetable/generate   — run CP-SAT solver (up to 5 min)
 *   3. GET  /api/timetable/sections   — view section timetables
 *   4. GET  /api/timetable/teachers   — view teacher timetables
 *   5. GET  /api/timetable/conflicts  — view data gaps and scheduling conflicts
 */
@SpringBootApplication
public class TimetableschedulerApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(TimetableschedulerApplication.class)
				.headless(false)
				.run(args);
	}

}
