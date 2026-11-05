# SatSched-Core — Full-School Constraint-Based Timetable Solver

A complete school timetable generator that automatically creates conflict-free weekly schedules for all 41 sections across 44 teachers using Google OR-Tools CP-SAT. Built with **Java 21 + Spring Boot** (backend) and **Next.js 14** (frontend).

> 🎥 **Demo Walkthrough Video:** [Watch on Google Drive](https://drive.google.com/file/d/1SPsH9Fj9u9TBX8FEOTyFD_vlt0D33-6M/view?usp=sharing)

| Resource | URL | Description |
|---|---|---|
| **Demo Video** | [Google Drive Demo](https://drive.google.com/file/d/1SPsH9Fj9u9TBX8FEOTyFD_vlt0D33-6M/view?usp=sharing) | Video walkthrough demonstrating the scheduler and UI |
| **Frontend UI** | [http://localhost:3000](http://localhost:3000) | Interactive dashboard, section timetables, teacher views |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive OpenAPI / Swagger documentation & test console |
| **Backend API** | [http://localhost:8080/api/timetable](http://localhost:8080/api/timetable) | REST API root endpoint |
| **OpenAPI Spec** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | OpenAPI 3 JSON specification |

---

## Table of Contents

1. [What This Project Does](#1-what-this-project-does)
2. [How It Works — The Big Picture](#2-how-it-works--the-big-picture)
3. [Project Structure](#3-project-structure)
4. [Prerequisites](#4-prerequisites)
5. [Running the Project](#5-running-the-project)
6. [How to Generate a Schedule](#6-how-to-generate-a-schedule)
7. [API Reference](#7-api-reference)
8. [Understanding the Output](#8-understanding-the-output)
9. [Configuration](#9-configuration)
10. [Dataset Files](#10-dataset-files)
11. [Scheduling Rules](#11-scheduling-rules)
12. [Known Data Gaps](#12-known-data-gaps)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What This Project Does

SatSched-Core takes raw school data (teachers, sections, subjects, and a bell schedule) and automatically produces a valid weekly timetable that satisfies dozens of constraints — for example:

- No teacher teaches two classes at the same time.
- Every section gets exactly 52 teaching periods per week.
- Subjects like Games and Library are never placed in the first period pair.
- Subjects sharing the same first word (e.g. "English Language" and "English Literature") never appear on the same day for the same section.
- Math is preferred in morning slots.

The solver runs Google OR-Tools CP-SAT — a professional constraint programming solver — under the hood, so it finds a mathematically valid schedule or tells you exactly why one is impossible.

**School scale this is designed for:**

| Metric | Value |
|---|---:|
| Active sections | 41 |
| Teaching periods per section per week | 52 |
| Total slots to fill | 2,132 |
| Active assignment rows | 587 |
| Teachers with assignments | 44 |

---

## 2. How It Works — The Big Picture

The process has three simple stages:

```
Stage 1 — Load Data         Stage 2 — Solve             Stage 3 — View Results
───────────────────         ────────────────             ──────────────────────
Read source files    ──►    Build a constraint    ──►    REST API + Next.js UI
(JSON, Excel, Markdown)     model and run              shows timetables,
                            the CP-SAT solver           teacher views, conflicts
```

**Inside Stage 2, the solver does this:**

1. Creates one binary decision variable per (section, subject, teacher, day, period) combination.
2. Adds hard rules as constraints — e.g. "teacher can only be in one place at a time".
3. Adds a soft objective — minimize Math lessons outside morning slots.
4. Runs the OR-Tools CP-SAT solver (up to 5 minutes by default).
5. Extracts the winning assignments and stores them in memory.

---

## 3. Project Structure

```
SatSched-Core/
│
├── timetablescheduler/          ← Spring Boot backend (Maven)
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../
│       │   ├── api/             ← REST controllers
│       │   ├── config/          ← Solver config bean
│       │   ├── data/            ← DTOs and shared SolverContext
│       │   ├── loader/          ← Parsers for each data source
│       │   ├── solver/          ← CP-SAT model builder and executor
│       │   └── reporting/       ← Timetable and conflict reporters
│       └── resources/
│           └── application.properties
│
├── frontend/                    ← Next.js 14 frontend
│   ├── package.json
│   └── src/
│       ├── app/
│       │   ├── page.tsx         ← Dashboard (load + generate buttons)
│       │   ├── sections/        ← Section timetable grid
│       │   ├── teachers/        ← Teacher timetable view
│       │   └── conflicts/       ← Data gap and conflict report
│       ├── components/          ← Shared UI components
│       └── lib/api.ts           ← Typed API client
│
├── datasets/                    ← Input data files
│   ├── sections.json            ← 41 school sections
│   ├── bell-schedule.json       ← Period timings (Mon–Thu vs Fri–Sat)
│   └── scheduling-rules.json    ← Constraint rules
│
├── CLASS_WISE_SUBJECTS.md       ← Curriculum per grade (52 periods/week)
├── TEACHER_CLASS_ASSIGNMENTS.md ← Teacher roster and class-level load
└── TEACHER_CLASS_ALLOCATION.xlsx← Section-level assignments (587 rows)
```

---

## 4. Prerequisites

Make sure you have these installed before starting:

| Tool | Version | What it's for |
|---|---|---|
| **Java JDK** | 21 or higher | Running the Spring Boot backend |
| **Maven** | 3.8+ | Building the backend |
| **Node.js** | 18 or higher | Running the Next.js frontend |
| **npm** | 9+ | Installing frontend packages |

> **Check your versions:**
> ```bash
> java -version
> mvn -version
> node -version
> npm -version
> ```

No database setup is required — all data is loaded from files into memory at runtime.

---

## 5. Running the Project

You need to start **two servers**: the backend API and the frontend UI. Use two separate terminal windows.

### Step 1 — Start the Backend (Spring Boot)

```bash
cd timetablescheduler
mvn spring-boot:run
```

The first run will download Maven dependencies (including the OR-Tools native library — around 150 MB). This takes a few minutes on the first run only.

Once you see this line, the backend is ready:

```
Started TimetableschedulerApplication in X.XXX seconds
```

The backend is now running at **http://localhost:8080**  
A browser window will automatically pop up with **Swagger UI** at:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**  
*(You can disable this behavior anytime by setting `app.open-browser-on-startup=false` in `application.properties`)*.

> **Note:** The backend reads data files relative to the project root folder. Always run this command from inside the `timetablescheduler/` directory.

### Step 2 — Start the Frontend (Next.js)

Open a **new terminal window** and run:

```bash
cd frontend
npm install        # only needed the first time
npm run dev
```

Once ready, you will see:

```
ready - started server on http://localhost:3000
```

The frontend UI is now available at **http://localhost:3000**

---

## 6. How to Generate a Schedule

Once both servers are running, follow these steps in order. You can do this either through the **web UI** or directly via **API calls**.

---

### Option A — Using the Web UI (Recommended)

Open your browser and go to **http://localhost:3000**

#### Step 1 — Load Data

Click the **"Load Data"** button on the dashboard.

This reads and parses all source files:
- `datasets/sections.json` — the 41 sections
- `datasets/bell-schedule.json` — period timings and breaks
- `datasets/scheduling-rules.json` — constraint rules
- `CLASS_WISE_SUBJECTS.md` — subjects and weekly period targets per grade
- `TEACHER_CLASS_ALLOCATION.xlsx` — which teacher teaches which subject in which section

A summary card will appear showing:
- How many sections were loaded
- How many assignments are schedulable
- How many rows have data issues (unassigned teachers, zero-workload rows)

#### Step 2 — Generate the Timetable

Click the **"Generate Timetable"** button.

The solver runs in the background. Depending on your machine, this takes **anywhere from a few seconds to 5 minutes**. A status badge shows the current state:

- 🟡 **Running** — solver is working
- 🟢 **FEASIBLE** — a valid schedule was found (all hard rules satisfied)
- 🟢 **OPTIMAL** — the best possible schedule was found
- 🔴 **INFEASIBLE** — no valid schedule exists with current data (see conflicts page)

#### Step 3 — View the Results

Use the navigation links to explore:

| Page | URL | What you see |
|---|---|---|
| **Section Timetable** | `/sections` | Pick any of the 41 sections from a dropdown; see their full Monday–Saturday grid |
| **Teacher Timetable** | `/teachers` | Pick any teacher; see all their classes across the week |
| **Conflicts** | `/conflicts` | See data gaps (unassigned teachers, zero-workload rows) and any scheduling conflicts |

---

### Option B — Using the API (curl or Postman)

#### Step 1 — Load Data

```bash
curl -X POST http://localhost:8080/api/timetable/load-data
```

Expected response:

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

#### Step 2 — Generate the Timetable

```bash
curl -X POST http://localhost:8080/api/timetable/generate
```

This call **blocks** until the solver finishes (up to 5 minutes by default). The response includes the full timetable, solver status, and any conflicts.

#### Step 3 — Fetch Results

```bash
# All section timetables
curl http://localhost:8080/api/timetable/sections

# One specific section (replace the ID with an actual section ID from /sections-list)
curl http://localhost:8080/api/timetable/sections/grade-10-lily

# All teacher timetables
curl http://localhost:8080/api/timetable/teachers

# One specific teacher (by teacher code)
curl http://localhost:8080/api/timetable/teachers/RVS115

# Conflicts and data gaps
curl http://localhost:8080/api/timetable/conflicts

# Current solver status
curl http://localhost:8080/api/timetable/status
```

---

## 7. API Reference

All endpoints are under `http://localhost:8080/api/timetable`.

### Interactive Documentation (Swagger UI)
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Endpoints Table

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/load-data` | Parse all source files and load data into memory |
| `POST` | `/generate` | Build constraint model, run solver, extract solution |
| `GET` | `/sections` | Return all 41 section timetables |
| `GET` | `/sections/{id}` | Return timetable for one section |
| `GET` | `/sections-list` | List all section IDs and names |
| `GET` | `/teachers` | Return all teacher timetables |
| `GET` | `/teachers/{code}` | Return timetable for one teacher |
| `GET` | `/teachers-list` | List all teacher codes and names |
| `GET` | `/conflicts` | Return data conflicts and scheduling conflicts |
| `GET` | `/status` | Return current solver status and statistics |

> **Important:** Always call `/load-data` before `/generate`. Calling `/generate` without loading data first returns a `400 Bad Request` with the message: `"Load data first: POST /api/timetable/load-data"`.

---

## 8. Understanding the Output

### Solver Status

| Status | Meaning |
|---|---|
| `FEASIBLE` | A valid schedule was found. Not proven to be the absolute best, but all hard rules are satisfied. |
| `OPTIMAL` | The best possible schedule was found — no further improvement is possible. |
| `INFEASIBLE` | No valid schedule exists with the current constraints and data. Check the conflicts report for the cause. |

### Sample Generate Response

```json
{
  "success": true,
  "solver": {
    "engine": "Google OR-Tools CP-SAT",
    "status": "FEASIBLE",
    "wallTimeMs": 45000,
    "timeLimitSeconds": 300,
    "objectiveValue": 8
  },
  "summary": {
    "sectionsTotal": 41,
    "sectionsScheduled": 38,
    "totalSlotsScheduled": 1976,
    "teachersInvolved": 44,
    "unassignedPlaceholderRows": 92,
    "zeroWorkloadRows": 142
  },
  "sectionTimetables": {
    "Grade 10 - Lily": {
      "MONDAY": [
        { "period": 1, "subject": "Mathematics", "teacherCode": "RVS115", "teacherName": "Ashish Thakur" },
        { "period": 2, "subject": "Mathematics", "teacherCode": "RVS115", "teacherName": "Ashish Thakur" }
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
  "dataConflicts": [ ... ],
  "schedulingConflicts": [ ... ]
}
```

### Conflict Types

| Type | Meaning |
|---|---|
| `UNASSIGNED_TEACHER` | A row in the Excel sheet has `UNASSIGNED-TT` as the teacher — cannot be scheduled |
| `ZERO_WORKLOAD` | A row has 0 periods per week — excluded from scheduling |
| `NO_ASSIGNMENTS` | A section has no assignment data at all |
| `CURRICULUM_MISMATCH` | A subject appears in the curriculum file but has no matching assignment in the Excel sheet |

---

## 9. Configuration

Backend settings are in [`timetablescheduler/src/main/resources/application.properties`](timetablescheduler/src/main/resources/application.properties):

| Property | Default | What it controls |
|---|---|---|
| `server.port` | `8080` | Port the backend runs on |
| `data.base.path` | `../` | Where to look for source data files (relative to `timetablescheduler/`) |
| `solver.timeLimitSeconds` | `300` | Max seconds the solver runs. Increase to allow more optimization time. |
| `solver.numWorkers` | `0` (all cores) | CPU cores used by the solver. Set to e.g. `4` to limit usage on shared machines. |
| `solver.relativeGapLimit` | `0.1` | Stop when solution is within 10% of proven optimal. Set to `0` to always prove optimal (slower). |
| `solver.logProgress` | `false` | Set to `true` to print detailed CP-SAT search logs in the console. |

---

## 10. Dataset Files

All input files live in the project root and the `datasets/` folder.

### `datasets/sections.json`
Lists all 41 active sections with their grade and curriculum key (e.g. `"Class 10"`).

### `datasets/bell-schedule.json`
Defines the bell schedule for the week:
- **Monday–Thursday:** 8 teaching slots (07:45 – 13:30) with a short break and lunch break
- **Friday–Saturday:** 10 teaching slots (07:45 – 13:30) with a short break and lunch break

Break slots are marked and are never used for teaching.

### `datasets/scheduling-rules.json`
Contains the constraint rules (mapped to engine Rule IDs) that are loaded and applied during CP-SAT model building.

### `CLASS_WISE_SUBJECTS.md`
The curriculum for each grade — which subjects are taught, their maximum daily periods, and required weekly periods. Every grade totals exactly **52 periods per week**.

### `TEACHER_CLASS_ALLOCATION.xlsx`
The primary data source. Contains 587 rows — one row per teacher-subject-section assignment — with the weekly period count. Rows with `UNASSIGNED-TT` or zero workload are flagged as data conflicts.

### `TEACHER_CLASS_ASSIGNMENTS.md`
Teacher roster with class-level summaries. Used as a reference; the Excel file is the authoritative assignment source.

---

## 11. Scheduling Rules

The solver enforces these rules automatically. They are grouped into **hard constraints** (never broken) and **soft preferences** (best-effort).

### Hard Constraints

| Rule | What it enforces |
|---|---|
| **H1** | No teacher can be in two classes at the same time |
| **H3** | Each section has exactly one lesson per teaching slot |
| **H4** | Break slots are never used for teaching |
| **H5** | Block subjects (2 periods/day) are always placed as consecutive pairs — never split |
| **H7** | A subject cannot exceed its `maxPeriodsPerDay` limit on any single day |
| **H8** | Each subject meets its exact `periodsPerWeek` target across the week |
| **G1** | Games and Library cannot be placed in the first period pair of any day |
| **G2** | For Grade 11 and 12: Games and Library cannot both appear on the same day |
| **L1** | Subjects sharing the same first word cannot be on the same day for the same section |

### Soft Preference

| Rule | What it tries to do |
|---|---|
| **PR-MATH** | Mathematics is preferred in periods 1–4 on Monday–Thursday. The solver minimizes the number of Math lessons placed outside this morning window. The `objectiveValue` in the response counts how many Math periods ended up outside morning slots. |

---

## 12. Known Data Gaps

The current dataset has incomplete information that limits scheduling. The solver reports these explicitly rather than silently ignoring them.

| Gap | Count | Effect |
|---|---|---|
| `UNASSIGNED-TT` teacher placeholders | 92 rows | These subject-section pairs cannot be scheduled |
| Zero-workload rows | 142 rows | Excluded from scheduling |
| Missing teacher availability records | All 44 teachers | Solver assumes all teachers are available every day |
| No specialist rooms (Computer Lab, Science Lab) | — | Room-type constraints are skipped |
| Only 9 rooms for 41 sections | — | Room assignment is not enforced in the current version |

> To get a more complete schedule, the 92 `UNASSIGNED-TT` rows need real teacher assignments before running the solver.

---

## 13. Troubleshooting

### "Data not loaded" error when calling `/generate`
You must call `POST /api/timetable/load-data` first. The server holds all data in memory — if the backend restarts, you need to load data again before generating.

### Backend fails to start with a Maven / Java error
Make sure your Java version is 21 or higher (`java -version`). Also ensure you are inside the `timetablescheduler/` directory when running `mvn spring-boot:run`.

### First Maven build is very slow
The first run downloads the Google OR-Tools native library (around 150 MB). Subsequent runs use the cached version and start in seconds.

### Solver returns `INFEASIBLE`
Check `GET /api/timetable/conflicts`. The most common cause is a teacher assigned to more than 52 periods across all their classes (the weekly slot capacity). The response will list the specific teachers and period counts.

### Frontend shows "Network Error" or a blank page
Make sure the backend is running on port 8080 before opening the frontend. The frontend connects to `http://localhost:8080` — it will not work without the backend running.

### Generation takes too long
Reduce `solver.timeLimitSeconds` in `application.properties` (e.g. to `60`). The solver returns the best solution found so far when the time limit is reached — a shorter limit means a faster but possibly less optimal result.

### The timetable is different every time I generate
This is expected and by design. The CP-SAT solver explores the search space in a different order each run. Every result satisfies all hard constraints — only the soft objective score (Math in morning slots) may differ slightly between runs.

