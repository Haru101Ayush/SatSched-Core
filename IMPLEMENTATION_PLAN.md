# Implementation Plan — Full-School Constraint-Based Timetable Solver

> **Assessment:** RDPL Spring Boot Developer Assessment  
> **Target:** Google OR-Tools CP-SAT solver (Java 21 + Spring Boot + Next.js)  
> **Timeline:** 5–7 working days  
> **Scale:** 41 sections · 44 teachers · 2,132 teaching slots/week

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure](#2-project-structure)
3. [Day-by-Day Implementation Plan](#3-day-by-day-implementation-plan)
4. [Phase 1 — Project Bootstrap & Data Models](#phase-1--project-bootstrap--data-models-day-1)
5. [Phase 2 — Data Loading & Normalization](#phase-2--data-loading--normalization-day-2)
6. [Phase 3 — CP-SAT Constraint Model](#phase-3--cp-sat-constraint-model-day-3--4)
7. [Phase 4 — REST API Layer](#phase-4--rest-api-layer-day-4)
8. [Phase 5 — Next.js Frontend UI](#phase-5--nextjs-frontend-ui-day-5)
9. [Phase 6 — Integration, Testing & Polish](#phase-6--integration-testing--polish-day-6--7)
10. [Constraint Implementation Reference](#10-constraint-implementation-reference)
11. [Data Gap Handling Strategy](#11-data-gap-handling-strategy)
12. [API Contract](#12-api-contract)
13. [CP-SAT Decision Variable Design](#13-cp-sat-decision-variable-design)
14. [Technology & Dependency Reference](#14-technology--dependency-reference)
15. [Assumptions & Limitations](#15-assumptions--limitations)

---

## 1. Architecture Overview

```
+------------------------------------------------------------------------+
|                      Next.js Frontend (Port 3000)                      |
|   Load Data --> Run Solver --> Section Grid --> Teacher View --> Gaps   |
+----------------------------------+-------------------------------------+
                                   | HTTP / JSON
+----------------------------------v-------------------------------------+
|                    Spring Boot REST API (Port 8080)                    |
|                                                                        |
|  +-------------+   +--------------------+   +---------------------+   |
|  | DataLoader  |-->| ConstraintBuilder  |-->|   SolverService     |   |
|  |  Service    |   |   (CpModel)        |   | (CpSolver+extract)  |   |
|  +-------------+   +--------------------+   +---------------------+   |
|        |                                             |                 |
|  +-----v---------------------------------------------v---------------+ |
|  |                  In-Memory State (SolverContext)                   | |
|  |  sections . assignments . curriculum . bellSchedule . solution     | |
|  +-------------------------------------------------------------------+ |
|                                                                        |
|  +-------------------------------------------------------------------+ |
|  |       Reporting Layer (conflicts . timetables . status)           | |
|  +-------------------------------------------------------------------+ |
+------------------------------------------------------------------------+
```

**Data flow (loading):**

```
sections.json ─────────────────────────────────┐
bell-schedule.json ─────────────────────────────┤
scheduling-rules.json ──────────────────────────┤──► DataLoaderService
CLASS_WISE_SUBJECTS.md ─────────────────────────┤         │
TEACHER_CLASS_ASSIGNMENTS.md ───────────────────┤         ▼
TEACHER_CLASS_ALLOCATION.xlsx ──────────────────┘   SolverContext
                                                         │
                                                         ▼
                                                   ConstraintBuilder
                                                         │
                                                         ▼
                                                   CpSolver (OR-Tools)
                                                         │
                                                         ▼
                                               Solution / INFEASIBLE report
```

---

## 2. Project Structure

```
timetable-solver/
├── backend/                                  <- Spring Boot project (Maven)
│   ├── pom.xml
│   └── src/main/java/com/rdpl/timetable/
│       ├── TimetableSolverApplication.java
│       │
│       ├── config/
│       │   └── SolverConfig.java             <- Bean config, time limits
│       │
│       ├── data/                             <- DTOs / model objects
│       │   ├── SectionDTO.java
│       │   ├── CurriculumItemDTO.java
│       │   ├── AssignmentDTO.java
│       │   ├── TeacherDTO.java
│       │   ├── TimeSlotDTO.java
│       │   ├── ScheduledLessonDTO.java
│       │   ├── ConflictDTO.java
│       │   └── SolverContext.java            <- Shared in-memory state
│       │
│       ├── loader/                           <- Data loading & normalization
│       │   ├── DataLoaderService.java        <- Orchestrates all loaders
│       │   ├── SectionsLoader.java           <- sections.json
│       │   ├── BellScheduleLoader.java       <- bell-schedule.json
│       │   ├── CurriculumLoader.java         <- CLASS_WISE_SUBJECTS.md
│       │   ├── AssignmentExcelLoader.java    <- TEACHER_CLASS_ALLOCATION.xlsx
│       │   ├── SchedulingRulesLoader.java    <- scheduling-rules.json
│       │   └── DataGapAnalyzer.java          <- UNASSIGNED-TT, zero-workload
│       │
│       ├── solver/                           <- CP-SAT engine
│       │   ├── TimetableSolverService.java   <- Orchestrates solve
│       │   ├── ConstraintBuilder.java        <- Builds CpModel with all rules
│       │   ├── VariableFactory.java          <- Creates BoolVar matrix
│       │   ├── HardConstraints.java          <- H1-H10, G1-G2, L1, T2, DATA-1
│       │   ├── BlockLayoutConstraints.java   <- H5, B1, B3b, B4
│       │   ├── SoftObjective.java            <- PR-MATH, soft penalties
│       │   └── SolutionExtractor.java        <- Reads vars -> DTOs
│       │
│       ├── reporting/
│       │   ├── ReportingService.java         <- Section + teacher views
│       │   └── ConflictReporter.java         <- dataConflicts, schedulingConflicts
│       │
│       └── api/
│           ├── TimetableController.java      <- REST endpoints
│           └── SolverResponse.java           <- Unified response DTO
│
└── frontend/                                 <- Next.js application
    ├── package.json
    ├── next.config.js
    └── src/
        ├── app/
        │   ├── page.tsx                      <- Dashboard / home
        │   ├── sections/page.tsx             <- Section timetable grid
        │   ├── teachers/page.tsx             <- Teacher timetable view
        │   └── conflicts/page.tsx            <- Data gaps + scheduling conflicts
        ├── components/
        │   ├── TimetableGrid.tsx
        │   ├── SolverStatus.tsx
        │   ├── ConflictTable.tsx
        │   └── Navbar.tsx
        ├── hooks/
        │   └── useSolver.ts
        └── lib/
            └── api.ts                        <- Typed API client
```

---

## 3. Day-by-Day Implementation Plan

| Day | Focus | Deliverables |
| ---: | --- | --- |
| **1** | Bootstrap + Data Models | Project skeleton, DTOs, `pom.xml` with OR-Tools |
| **2** | Data Loading | Parse all source files, gap report, `SolverContext` populated |
| **3** | CP-SAT Core | Variables, Tier 1 hard constraints (H1, H3, H4, H7, H8, G1, G2, L1) |
| **4** | Full Constraints + API | Tier 2 block rules + REST endpoints returning real output |
| **5** | Next.js UI | Dashboard, section grid, teacher view, conflicts screen |
| **6** | Integration + Polish | End-to-end run on full 41-section dataset, fix failures |
| **7** | Buffer / Bonus | L2, L3, H9 fixed slots, soft objectives, README, sample output |

---

## Phase 1 — Project Bootstrap & Data Models (Day 1)

### 1.1 Maven Project Setup

```xml
<!-- pom.xml key dependencies -->
<dependencies>
  <!-- Google OR-Tools CP-SAT (Java binding) -->
  <dependency>
    <groupId>com.google.ortools</groupId>
    <artifactId>ortools-java</artifactId>
    <version>9.10.4067</version>
  </dependency>

  <!-- Excel parsing -->
  <dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
  </dependency>

  <!-- JSON -->
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>

  <!-- Spring Boot Web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <!-- Lombok -->
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>
</dependencies>
```

> **OR-Tools native loading:** Call `Loader.loadNativeLibraries()` in a `@PostConstruct` or at application startup before any model is created.

### 1.2 Core Data Transfer Objects (DTOs)

```java
// SectionDTO.java
public record SectionDTO(
    String id,
    String displayName,
    String grade,
    String section,
    String curriculumKey,
    int capacity
) {}

// CurriculumItemDTO.java
public record CurriculumItemDTO(
    String subjectName,
    int periodsPerWeek,
    int maxPeriodsPerDay,
    String type           // "Subject" | "Activity Group"
) {}

// AssignmentDTO.java
public record AssignmentDTO(
    String sectionId,
    String subjectName,
    String teacherId,
    String teacherName,
    int periodsPerWeek,
    boolean isUnassigned,    // true when teacherId == "UNASSIGNED-TT"
    boolean isZeroWorkload   // true when periodsPerWeek == 0
) {}

// TeacherDTO.java
public record TeacherDTO(String id, String code, String name) {}

// TimeSlotDTO.java
public record TimeSlotDTO(
    String day,           // "MONDAY" .. "SATURDAY"
    int dayIndex,         // 1..6
    int period,           // 1..10
    boolean teaching,
    String slotPair       // "1A", "1B" ... for pair grouping
) {}

// ScheduledLessonDTO.java
public record ScheduledLessonDTO(
    String sectionId, String day, int period,
    String subjectName, String teacherCode, String teacherName
) {}

// ConflictDTO.java
public record ConflictDTO(
    String type,     // "UNASSIGNED_TEACHER" | "ZERO_WORKLOAD" | ...
    String section, String subject, String teacherCode, String message
) {}
```

### 1.3 SolverContext (Shared In-Memory State)

```java
@Component
public class SolverContext {
    public List<SectionDTO> sections;
    public Map<String, List<CurriculumItemDTO>> curriculumByKey;
    public List<AssignmentDTO> assignments;      // all 587 rows
    public List<AssignmentDTO> schedulable;      // no UNASSIGNED, no zero
    public Map<String, TeacherDTO> teachers;     // code -> teacher
    public List<TimeSlotDTO> allSlots;           // all valid (day, period) pairs

    // Data quality issues found at load time
    public List<ConflictDTO> dataConflicts = new ArrayList<>();

    // Solution (populated after solve)
    public List<ScheduledLessonDTO> solution = new ArrayList<>();
    public String solverStatus;   // "FEASIBLE" | "OPTIMAL" | "INFEASIBLE"
    public long wallTimeMs;
    public double objectiveValue;
    public List<String> schedulingConflicts = new ArrayList<>();
}
```

---

## Phase 2 — Data Loading & Normalization (Day 2)

### 2.1 SectionsLoader — `sections.json`

```
Action:
- Jackson ObjectMapper -> List<SectionDTO>
- Validate: all 41 sections present
- Note: Pre-Nursery, Nursery, Junior KG, Senior KG -> document
  handling (skip per rule P4 or include using xlsx assignment data)
```

### 2.2 BellScheduleLoader — `bell-schedule.json`

```
Mon-Thu:  8 teaching periods (1-8)
Fri-Sat: 10 teaching periods (1-10)
Break slots: excluded from variable creation (H4)

Build: List<TimeSlotDTO> with teaching=true only
Build: Map<Integer, List<int[]>> slotPairs per dayIndex
       for block constraint use -> [(1,2), (3,4), (5,6), (7,8)]
```

### 2.3 CurriculumLoader — `CLASS_WISE_SUBJECTS.md`

```
Strategy: regex / line-based markdown table parser

For each section "### Class N":
  - Parse table rows: type, subject name, periodsPerDay, periodsPerWeek
  - Validate: sum(periodsPerWeek) == 52 for every grade

Result: Map<String, List<CurriculumItemDTO>>
  e.g. "Class 10" -> [Math(10,2), EnglishLang(6,2), ...]
```

### 2.4 AssignmentExcelLoader — `TEACHER_CLASS_ALLOCATION.xlsx`

```
Library: Apache POI (XSSFWorkbook)
Sheet: "Section Assignments"

Columns (expected):
  Section name | Subject name | Teacher code | Teacher name | workload_per_week

Normalization steps:
  1. Match section name -> SectionDTO.id (normalize whitespace/case)
  2. Match subject name -> CurriculumItemDTO (fuzzy match tolerated; log mismatches)
  3. Flag isUnassigned = (teacherCode.equalsIgnoreCase("UNASSIGNED-TT"))
  4. Flag isZeroWorkload = (periodsPerWeek <= 0)
  5. Build ConflictDTO for every flagged row

Result: List<AssignmentDTO> (all 587)
Filtered schedulable: exclude isUnassigned OR isZeroWorkload
```

### 2.5 DataGapAnalyzer

```java
// Produces ConflictDTO entries for:
// (a) 92 UNASSIGNED-TT rows -> type: "UNASSIGNED_TEACHER"
// (b) 142 zero-workload rows -> type: "ZERO_WORKLOAD"
// (c) Sections with no assignment data -> type: "NO_ASSIGNMENTS"
// (d) Curriculum mismatch (subject in md but not in xlsx) -> type: "CURRICULUM_MISMATCH"

// All populate SolverContext.dataConflicts
// NEVER silently drop any row
```

### 2.6 POST `/api/timetable/load-data` response

```json
{
  "loaded": true,
  "summary": {
    "sections": 41,
    "schedulableAssignments": 353,
    "unassignedRows": 92,
    "zeroWorkloadRows": 142,
    "teachers": 44
  },
  "dataConflicts": [ ... ]
}
```

---

## Phase 3 — CP-SAT Constraint Model (Day 3–4)

### 3.1 Decision Variable Design

Each schedulable assignment uses **BoolVar** variables:

```
X[sectionId, subjectName, teacherCode, dayIndex, period] in {0, 1}

Meaning:
  1 = this (section, subject, teacher) is scheduled on (day, period)
  0 = it is not

Teacher is FIXED per (section, subject) pair from assignment data.
The solver only decides WHEN (day, period) — not WHO teaches.

Approximate count:
  41 sections x 12 avg subjects x 52 slots = ~25,500 BoolVars
  Many pre-fixed to 0 by H4, H6, H10, DATA-1 before solving.
```

Variable storage:
```java
// 5-level nested map for O(1) lookup
Map<String, Map<String, Map<String, Map<Integer, Map<Integer, BoolVar>>>>> vars;
// section  -> subject -> teacher -> day -> period -> BoolVar
```

### 3.2 HardConstraints.java

#### H1 — No teacher double-booking

```java
// For every teacher x day x period:
//   sum of all BoolVars where that teacher is booked <= 1
for (TeacherDTO teacher : teachers) {
    for (TimeSlotDTO slot : teachingSlots) {
        List<BoolVar> sameDayPeriodVars =
            getAllVarsForTeacherDayPeriod(teacher.code(), slot.dayIndex(), slot.period());
        if (sameDayPeriodVars.size() > 1) {
            model.addAtMostOne(sameDayPeriodVars);   // H1
        }
    }
}
```

#### H3 — One lesson per section per valid slot

```java
// For every section x day x period:
//   sum of all subject vars in that slot == 1 (exactly one)
for (SectionDTO sec : sections) {
    for (TimeSlotDTO slot : teachingSlots) {
        List<BoolVar> allSubjectsInSlot =
            getAllVarsForSectionDayPeriod(sec.id(), slot.dayIndex(), slot.period());
        model.addExactlyOne(allSubjectsInSlot);     // H3
    }
}
```

#### H7 — Daily subject max (`maxPeriodsPerDay`)

```java
// For every section x subject x day:
//   sum(vars for that day) <= curricItem.maxPeriodsPerDay
for (SectionDTO sec : sections) {
    for (AssignmentDTO asgn : assignmentsFor(sec)) {
        CurriculumItemDTO item = curriculumFor(sec, asgn.subjectName());
        for (int day = 1; day <= 6; day++) {
            List<BoolVar> dayVars = getVarsForSubjectDay(asgn, day);
            model.addLinearConstraint(
                LinearExpr.sum(dayVars), 0, item.maxPeriodsPerDay());  // H7
        }
    }
}
```

#### H8 — Weekly curriculum totals

```java
// For every schedulable assignment:
//   sum(all week vars) == asgn.periodsPerWeek
for (AssignmentDTO asgn : ctx.schedulable) {
    List<BoolVar> weekVars = getAllVarsForAssignment(asgn);
    model.addEquality(LinearExpr.sum(weekVars), asgn.periodsPerWeek()); // H8
}
```

#### G1 — Games & Library not in periods 1 or 2

```java
for (AssignmentDTO asgn : ctx.schedulable) {
    if (isGamesOrLibrary(asgn.subjectName())) {
        for (int day = 1; day <= 6; day++) {
            for (int period : List.of(1, 2)) {
                BoolVar v = getVar(asgn, day, period);
                if (v != null) model.addEquality(v, 0);   // G1
            }
        }
    }
}
```

#### G2 — Class 11 & 12: Games and Library not on same day

```java
for (SectionDTO sec : class11And12Sections()) {
    AssignmentDTO gamesAsgn = getAssignment(sec, "Games");
    AssignmentDTO libAsgn   = getAssignment(sec, "Library");
    for (int day = 1; day <= 6; day++) {
        List<BoolVar> gamesDay   = getVarsForSubjectDay(gamesAsgn, day);
        List<BoolVar> libraryDay = getVarsForSubjectDay(libAsgn, day);
        List<BoolVar> combined   = concat(gamesDay, libraryDay);
        model.addLinearConstraint(
            LinearExpr.sum(combined), 0, 1);              // G2
    }
}
```

#### L1 — Same-first-word subjects cannot share a day

```java
// Group assignments by first word of subject name (case-insensitive)
Map<String, List<AssignmentDTO>> firstWordGroups = groupByFirstWord(ctx.schedulable);

for (SectionDTO sec : sections) {
    for (Map.Entry<String, List<AssignmentDTO>> entry : firstWordGroups.entrySet()) {
        if (entry.getValue().size() < 2) continue;
        for (int day = 1; day <= 6; day++) {
            List<BoolVar> dayVars = entry.getValue().stream()
                .filter(a -> a.sectionId().equals(sec.id()))
                .flatMap(a -> getVarsForSubjectDay(a, day).stream())
                .collect(toList());
            if (!dayVars.isEmpty())
                model.addLinearConstraint(LinearExpr.sum(dayVars), 0, 1); // L1
        }
    }
}
```

### 3.3 BlockLayoutConstraints.java

#### B1 / H5 — Block subjects in consecutive slot pairs only

Block subjects: `maxPeriodsPerDay >= 2`

```java
// Strategy: introduce BoolVar blockVar per (section, subject, day, pairIndex)
// blockVar == 1 iff X[day, pA] == 1 AND X[day, pB] == 1

// For each block assignment + day:
List<int[]> pairs = getConsecutiveNonBreakPairs(day);
// e.g. Mon-Thu: [(1,2), (3,4), (5,6), (7,8)]

for (int[] pair : pairs) {
    BoolVar blockVar = model.newBoolVar("block_" + ...);
    BoolVar xA = getVar(asgn, day, pair[0]);
    BoolVar xB = getVar(asgn, day, pair[1]);

    // blockVar=1 implies xA=1 and xB=1
    model.addImplication(blockVar, xA);
    model.addImplication(blockVar, xB);
    // xA=1 AND xB=1 implies blockVar=1
    model.addBoolOr(new Literal[]{blockVar, xA.not(), xB.not()});
}

// Lone singles forbidden: if period p is not part of any pair -> var=0
// (ensures block subjects are NEVER placed as orphan singles -- H5)
for (int period : nonPairedPeriods) {
    model.addEquality(getVar(asgn, day, period), 0);
}
```

#### B4 — Same-first-word singles cannot share a slot-pair

```java
// For each day x slot-pair x section:
//   sum of same-first-word single-period subject vars in that pair <= 1
for (String group : firstWordGroups.keySet()) {
    List<AssignmentDTO> singles = firstWordGroups.get(group).stream()
        .filter(a -> curriculumFor(a).maxPeriodsPerDay() == 1)
        .collect(toList());
    if (singles.size() < 2) continue;
    for (SectionDTO sec : sections) {
        for (int day = 1; day <= 6; day++) {
            for (int[] pair : getSlotPairs(day)) {
                List<BoolVar> pairVars = singles.stream()
                    .filter(a -> a.sectionId().equals(sec.id()))
                    .flatMap(a -> Stream.of(
                        getVar(a, day, pair[0]),
                        getVar(a, day, pair[1])))
                    .filter(Objects::nonNull)
                    .collect(toList());
                if (pairVars.size() > 1)
                    model.addLinearConstraint(LinearExpr.sum(pairVars), 0, 1); // B4
            }
        }
    }
}
```

### 3.4 SoftObjective — PR-MATH

```java
// Prefer Mathematics in periods 1-4 on Mon-Thu
// Penalty = number of Math periods scheduled OUTSIDE morning slots

List<BoolVar> mathNotMorning = new ArrayList<>();
for (AssignmentDTO asgn : mathAssignments()) {
    for (TimeSlotDTO slot : ctx.allSlots) {
        if (isMorningSlot(slot)) continue;    // periods 1-4, Mon-Thu
        BoolVar v = getVar(asgn, slot.dayIndex(), slot.period());
        if (v != null) mathNotMorning.add(v);
    }
}
// Minimize total penalty (1 per non-morning Math period)
model.minimize(LinearExpr.sum(mathNotMorning));
```

### 3.5 Solver Execution

```java
CpSolver solver = new CpSolver();
solver.getParameters().setMaxTimeInSeconds(300);
solver.getParameters().setNumSearchWorkers(8);  // use available CPU cores
solver.getParameters().setLogSearchProgress(true);

long startMs = System.currentTimeMillis();
CpSolverStatus status = solver.solve(model);
ctx.wallTimeMs = System.currentTimeMillis() - startMs;

switch (status) {
    case OPTIMAL, FEASIBLE -> {
        ctx.solverStatus = status.name();
        ctx.objectiveValue = solver.objectiveValue();
        solutionExtractor.extract(solver, vars, ctx);
    }
    case INFEASIBLE -> {
        ctx.solverStatus = "INFEASIBLE";
        ctx.schedulingConflicts.add("Model is infeasible — check teacher capacity vs demand.");
    }
    default -> ctx.solverStatus = status.name(); // MODEL_INVALID, UNKNOWN
}
```

---

## Phase 4 — REST API Layer (Day 4)

### 4.1 TimetableController

```java
@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = "http://localhost:3000")
public class TimetableController {

    @PostMapping("/load-data")
    public ResponseEntity<?> loadData() { ... }

    @PostMapping("/generate")
    public ResponseEntity<SolverResponse> generate() { ... }

    @GetMapping("/sections")
    public ResponseEntity<?> getSections() { ... }

    @GetMapping("/sections/{id}")
    public ResponseEntity<?> getSection(@PathVariable String id) { ... }

    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() { ... }

    @GetMapping("/teachers/{code}")
    public ResponseEntity<?> getTeacher(@PathVariable String code) { ... }

    @GetMapping("/conflicts")
    public ResponseEntity<?> getConflicts() { ... }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() { ... }
}
```

### 4.2 Unified SolverResponse

```json
{
  "success": true,
  "solver": {
    "engine": "Google OR-Tools CP-SAT",
    "status": "FEASIBLE",
    "wallTimeMs": 185000,
    "timeLimitSeconds": 300,
    "objectiveValue": 12
  },
  "summary": {
    "sectionsTotal": 41,
    "sectionsScheduled": 38,
    "totalSlotsScheduled": 1976,
    "teachersInvolved": 44,
    "unassignedPlaceholderRows": 92,
    "zeroWorkloadRows": 142
  },
  "dataConflicts": [ ... ],
  "schedulingConflicts": [ ... ],
  "sectionTimetables": {
    "Grade 10 - Lily": {
      "MONDAY": [
        { "period": 1, "subject": "Mathematics", "teacherCode": "RVS115", "teacherName": "Ashish Thakur" }
      ]
    }
  },
  "teacherTimetables": {
    "Ashish Thakur": {
      "MONDAY": [
        { "period": 1, "section": "Grade 10 - Lily", "subject": "Mathematics" }
      ]
    }
  },
  "validation": {
    "hardConstraintsSatisfied": true,
    "softConstraintPenalty": 12,
    "violatedSoftConstraints": []
  }
}
```

---

## Phase 5 — Next.js Frontend UI (Day 5)

### 5.1 Project initialization

```bash
cd timetable-solver/
npx create-next-app@latest frontend --typescript --tailwind --app
cd frontend && npm install axios lucide-react clsx
```

### 5.2 Pages & Components

#### `/` — Dashboard

- **Load Data** button -> `POST /api/timetable/load-data`
- Data summary card (sections, teachers, conflicts)
- **Generate Timetable** button -> `POST /api/timetable/generate`
- Solver status badge (FEASIBLE / INFEASIBLE / running)
- Wall time display, objective value
- Navigation links to all three views

#### `/sections` — Section Timetable Grid

- Dropdown: select section (41 options)
- 6-column grid (Mon–Sat) x 10 rows (periods 1–10)
- Each cell: subject name + teacher initials
- Color coding by subject type
- Break rows visually distinct

#### `/teachers` — Teacher Timetable View

- Dropdown: select teacher (44 options)
- Same 6x10 grid format
- Each cell: section name + subject
- Double-booked slots highlighted in red

#### `/conflicts` — Conflict Report

- Table 1: **Data Conflicts** — UNASSIGNED-TT rows, zero-workload rows
- Table 2: **Scheduling Conflicts** — sections/assignments that could not be scheduled

### 5.3 API client (`lib/api.ts`)

```typescript
const BASE = "http://localhost:8080/api/timetable";

export const api = {
  loadData:   () => axios.post(`${BASE}/load-data`),
  generate:   () => axios.post(`${BASE}/generate`),
  getSections:() => axios.get(`${BASE}/sections`),
  getSection: (id: string) => axios.get(`${BASE}/sections/${id}`),
  getTeachers:() => axios.get(`${BASE}/teachers`),
  getConflicts:()=> axios.get(`${BASE}/conflicts`),
};
```

---

## Phase 6 — Integration, Testing & Polish (Day 6–7)

### 6.1 End-to-End Validation Checklist

- [ ] `POST /api/timetable/load-data` — confirms 41 sections, 587 rows loaded
- [ ] `POST /api/timetable/generate` — returns within 5 minutes
- [ ] H1: no teacher appears in two sections at same day/period
- [ ] H3: every section has exactly 52 scheduled periods
- [ ] H7: no subject exceeds `maxPeriodsPerDay` on any day
- [ ] H8: each assignment has exactly `periodsPerWeek` scheduled periods
- [ ] G1: no Games/Library in periods 1–2
- [ ] G2: Class 11/12 — Games and Library never on same day
- [ ] L1: no two same-first-word subjects in same section on same day
- [ ] `dataConflicts` contains all 92 UNASSIGNED-TT + 142 zero-workload entries
- [ ] Next.js UI loads at `http://localhost:3000`, all pages functional
- [ ] Full run reproducible from `README.md` alone

### 6.2 Post-Solve Hard Constraint Validator

```java
@Component
public class SolutionValidator {
    public ValidationReport validate(List<ScheduledLessonDTO> lessons) {
        checkNoTeacherDoubleBooking(lessons);    // H1
        checkSectionSlotUniqueness(lessons);     // H3
        checkDailySubjectCap(lessons);           // H7
        checkWeeklyQuotas(lessons);              // H8
        checkGamesLibraryNotFirstPair(lessons);  // G1
        checkClass1112GamesLibrary(lessons);     // G2
        checkSameFirstWordSameDay(lessons);      // L1
        return report;
    }
}
```

### 6.3 README Requirements

The final `README.md` must include:

1. **Setup** — Java 21, Maven, `mvn spring-boot:run`, `npm run dev`
2. **CP-SAT variable design** — matrix description + approximate size
3. **Constraint -> Rule ID mapping table** (all AC, must reference Rule IDs)
4. **Data loading strategy** — which file -> which loader class
5. **Assumptions** — availability, Pre-Primary, rooms, activity instructors
6. **Known limitations** — infeasible sections, partial solve
7. **Sample output JSON** — truncated full-run result

---

## 10. Constraint Implementation Reference

| Rule ID | Tier | CP-SAT Approach | Status |
| --- | :---: | --- | :---: |
| **H1** | 1 | `addAtMostOne` per teacher x day x period | Required |
| **H2** | 1 | Set vars = 0 for unavailable slots (assume fully available) | Required |
| **H3** | 1 | `addExactlyOne` per section x day x period | Required |
| **H4** | 1 | No variable created for break slots | Required |
| **H5** | 2 | Block indicator vars + implication chains | Required |
| **H6** | 1 | Set vars = 0 outside `dayRestrictions` days | Required |
| **H7** | 1 | `addLinearConstraint(sum(day) <= maxPerDay)` | Required |
| **H8** | 1 | `addEquality(sum(week) == periodsPerWeek)` | Required |
| **H9** | 1 | Fix activity vars to `fixedSlots` only | Bonus |
| **H10** | 1 | No variable created for zero-workload assignments | Required |
| **G1** | 1 | Set Games/Library vars = 0 for periods 1–2 | Required |
| **G2** | 1 | `sum(games + library per day) <= 1` for Class 11 & 12 | Required |
| **L1** | 1 | `sum(same-first-word per day) <= 1` per section | Required |
| **L2** | 1 | Forbid adjacent across-break pairs for same subject | Bonus |
| **L3** | 1 | Window of 4 consecutive slots: `sum <= 3` | Bonus |
| **T2** | 1 | Teacher fixed per assignment; no dynamic inference | Required |
| **DATA-1** | 1 | No variable created for UNASSIGNED-TT rows | Required |
| **B1** | 2 | Consecutive non-break pair enforcement | Required |
| **B3b** | 2 | Per-day and per-week strict caps (H7 + H8) | Required |
| **B4** | 2 | `sum(same-first-word singles in pair) <= 1` | Required |
| **PR-MATH** | 3 | Minimize Math periods outside morning slots | Bonus |
| **R1–R2** | 4 | Room type mapping + no double-booking | Deferred |
| **M1–M6** | 4 | Merged section mirroring | Deferred |

---

## 11. Data Gap Handling Strategy

| Gap | Count | Action | Output Location |
| --- | ---: | --- | --- |
| `UNASSIGNED-TT` rows | 92 | Flag at load time; no variable created | `dataConflicts[]` |
| Zero-workload rows | 142 | Flag at load time; excluded from scheduling | `dataConflicts[]` |
| Teacher availability | 0 rows | Assume fully available (documented in README) | README assumption |
| Missing classrooms | 41 sections / 9 rooms | Skip room-type hard rules; document | README assumption |
| Pre-Primary sections | 4 grades | Attempt from xlsx; skip if no curriculum map per P4 | `dataConflicts[]` |
| Section/curriculum mismatch | Variable | List affected section-subjects | `dataConflicts[]` |

---

## 12. API Contract

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/timetable/load-data` | Parse all source files; returns summary + `dataConflicts` |
| `POST` | `/api/timetable/generate` | Build model + solve + extract; returns full `SolverResponse` |
| `GET` | `/api/timetable/sections` | All 41 section timetables |
| `GET` | `/api/timetable/sections/{id}` | Single section timetable |
| `GET` | `/api/timetable/teachers` | All 44 teacher combined weekly timetables |
| `GET` | `/api/timetable/teachers/{code}` | Single teacher timetable |
| `GET` | `/api/timetable/conflicts` | `dataConflicts` + `schedulingConflicts` |
| `GET` | `/api/timetable/status` | Last solver run status, wall time, objective value |

---

## 13. CP-SAT Decision Variable Design

```
X[s, u, t, d, p]  in {0, 1}

  s = section ID          (41 values)
  u = subject/activity    (up to ~15 per section)
  t = teacher code        (1 per assignment — FIXED from xlsx)
  d = day index           (1..6 : Mon=1 .. Sat=6)
  p = period number       (1..8 Mon-Thu | 1..10 Fri-Sat)

The solver only chooses WHEN (d, p) — not WHO teaches.
Teacher selection is deterministic from assignment data (T2).
```

**Constraint count estimates:**

| Constraint | Formula | Estimate |
| --- | --- | ---: |
| H1 (no double-booking) | 44 teachers x 52 slots | ~2,288 |
| H3 (section uniqueness) | 41 sections x 52 slots | ~2,132 |
| H7 (daily max) | 353 assignments x 6 days | ~2,118 |
| H8 (weekly quota) | 353 schedulable assignments | ~353 |
| L1 (same-first-word) | first-word groups x 41 x 6 | ~500 |
| Block constraints | ~200 block assignments x 6 x pairs | ~1,200 |
| **Total estimate** | | **~8,000–12,000** |

CP-SAT handles millions of constraints comfortably; this problem is well within practical limits.

---

## 14. Technology & Dependency Reference

### Backend (Spring Boot)

| Dependency | Purpose | Version |
| --- | --- | --- |
| `spring-boot-starter-web` | REST API + embedded Tomcat | 3.3.x |
| `ortools-java` | Google CP-SAT solver (includes native libs) | 9.10.x |
| `poi-ooxml` | Apache POI — Excel `.xlsx` parsing | 5.3.x |
| `jackson-databind` | JSON (de)serialization | (Spring managed) |
| `lombok` | Boilerplate reduction | latest |
| `spring-boot-starter-test` | Unit & integration tests | 3.3.x |

### Frontend (Next.js)

| Package | Purpose |
| --- | --- |
| `next` 14+ | React framework with App Router |
| `typescript` | Type safety |
| `axios` | HTTP client |
| `tailwindcss` | Utility-first styling |
| `lucide-react` | Icon library |

### Runtime Requirements

| Component | Version |
| --- | --- |
| Java | **21+** (LTS) |
| Maven | 3.9+ |
| Node.js | 20+ LTS |
| OR-Tools native | Auto-loaded via `ortools-java` JAR (Windows / Linux / macOS) |

---

## 15. Assumptions & Limitations

### Assumptions (to document in README)

| # | Assumption |
| ---: | --- |
| A1 | All 44 teachers are **fully available** all 6 days — no `teacher_availability` rows exist in dataset |
| A2 | Pre-Primary sections attempted using xlsx data; skipped if no curriculum mapping found (rule P4) |
| A3 | Activity instructors are **not conflict-checked** (T6, AC8) — whole-school activities run simultaneously |
| A4 | Room allocation is **deferred** — 41 sections vs 9 rooms makes R1–R2 infeasible without extra data |
| A5 | Teacher is taken **exactly as assigned** in xlsx (T2) — no round-robin fallback (T3) |
| A6 | Merged sections (M1–M6) not implemented — deferred epic |
| A7 | Solver time limit **300 seconds (5 min)**; FEASIBLE solution returned if OPTIMAL not achieved |
| A8 | If xlsx sheet names differ from expected, mapping is documented in README and adjusted |

### Known Limitations

| # | Limitation |
| ---: | --- |
| L1 | 92 UNASSIGNED-TT rows cannot be scheduled; appear in `dataConflicts` only |
| L2 | 142 zero-workload rows produce no lessons |
| L3 | Truly infeasible constraint sets (e.g., teacher overloaded) return `INFEASIBLE` with explanation |
| L4 | Room-type constraints (R1–R2) not implemented; no `roomId` in solution |
| L5 | Substitution engine (SE1–SE8, SA1–SA8, ST1–ST2, AT1–AT7) explicitly out of scope |
| L6 | No database persistence — state resets on server restart |

---

> **First action:** Create the Maven project skeleton, add the `ortools-java` dependency,
> and verify `Loader.loadNativeLibraries()` runs successfully on your machine before
> writing any constraint code.
