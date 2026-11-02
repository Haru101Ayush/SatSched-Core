# RDPL — Spring Boot Timetable Solver Assessment Package

**Share this entire folder with the candidate.** Do not include files from the parent `Task` folder outside this directory.

---

## Start here

1. Read **`Candidate_Timetable_Assessment.md`** — task instructions, acceptance criteria, submission rules  
2. Read **`datasets/README.md`** — how to load and combine the dataset  
3. Implement the **Google OR-Tools CP-SAT** solver using the data and rules below  

**Time limit:** 5–7 working days (take-home)

---

## Package contents

| File / folder | Purpose |
| --- | --- |
| **`Candidate_Timetable_Assessment.md`** | Main assessment task |
| **`datasets/`** | JSON data + data loading guide |
| **`Timetable-Engine-Rules.md`** | Complete production rule reference (all Rule IDs) |
| **`Timetable-Engine-Rules-Coverage.md`** | Which rules are built-in / text / data / deferred |
| **`CLASS_WISE_SUBJECTS.md`** | Subject & activity requirements per grade (52 ppw) |
| **`TEACHER_CLASS_ASSIGNMENTS.md`** | Teacher roster & class-level assignments |
| **`TEACHER_CLASS_ALLOCATION.xlsx`** | **Section-level assignments (587 rows)** — primary assignment source |
| **`TIMETABLE_GENERATION_DATA_REQUIREMENTS.md`** | Bell schedule, rooms, data gaps, DB context |

### Inside `datasets/`

| File | Purpose |
| --- | --- |
| `sections.json` | All 41 active sections |
| `bell-schedule.json` | Teaching periods & breaks |
| `scheduling-rules.json` | Rules mapped to engine Rule IDs + CP-SAT hints |
| `school-sample.json` | Optional 5-class subset for local debugging |
| `README.md` | Data package guide |

---

## What to build

```text
Load full dataset (Excel + JSON + markdown)
        ↓
Constraint model (Google OR-Tools CP-SAT)
        ↓
Full-school timetables (41 sections) + conflict reports
```

**Required:** Java 21+ · Spring Boot (REST API) · Google OR-Tools CP-SAT (Java binding)  

**Required UI deliverable:** Build the UI using **Next.js** for timetable generation and results review (for example: upload/load data, run generation, section timetable view, teacher timetable view, conflict view).  

**Not required:** Authentication, production-grade database persistence, natural-language parser, substitution engine

---

## Before you send this package (HR / internal)

- [ ] Add **`TEACHER_CLASS_ALLOCATION.xlsx`** to this folder if not already present  
- [ ] Zip the **`Springboot Task`** folder and email or share the link  
- [ ] Do **not** include: `School_Timetable_Generator_Task.md`, `Candidate_Assessment_Evaluator_Guide.md`, `Timetable-Rules-Assessment-Alignment.md`  

---

## Questions

If sample data is unclear, document your assumption in the README and proceed.

Good luck.
