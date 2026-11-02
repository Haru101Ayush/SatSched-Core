const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/timetable";

export interface SectionDTO {
  id: string;
  displayName: string;
  grade: string;
  section: string;
  curriculumKey: string;
  capacity: number;
}

export interface TeacherDTO {
  id: string;
  code: string;
  name: string;
}

export interface LessonSlot {
  period: number;
  subject: string;
  teacherCode: string;
  teacherName: string;
  section?: string;
}

export interface ConflictDTO {
  type: string;
  section: string;
  subject: string;
  teacherCode: string;
  message: string;
}

export interface ConflictReport {
  dataConflicts: ConflictDTO[];
  schedulingConflicts: string[];
}

export interface SolverSummary {
  sections: number;
  schedulableAssignments: number;
  unassignedRows: number;
  zeroWorkloadRows: number;
  teachers: number;
}

export interface LoadDataResponse {
  loaded: boolean;
  summary: SolverSummary;
  dataConflicts: ConflictDTO[];
}

export interface SolverResponse {
  success: boolean;
  solver: {
    engine: string;
    status: string;
    wallTimeMs: number;
    timeLimitSeconds: number;
    objectiveValue: number;
  };
  summary: {
    sectionsTotal: number;
    sectionsScheduled: number;
    totalSlotsScheduled: number;
    teachersInvolved: number;
    unassignedPlaceholderRows: number;
    zeroWorkloadRows: number;
  };
  dataConflicts: ConflictDTO[];
  schedulingConflicts: string[];
  sectionTimetables: Record<string, Record<string, LessonSlot[]>>;
  teacherTimetables: Record<string, Record<string, LessonSlot[]>>;
  validation: {
    hardConstraintsSatisfied: boolean;
    softConstraintPenalty: number;
    violatedSoftConstraints: string[];
  };
}

export const api = {
  loadData: async (): Promise<LoadDataResponse> => {
    const res = await fetch(`${API_BASE}/load-data`, { method: "POST" });
    if (!res.ok) throw new Error(`Failed to load data: ${res.statusText}`);
    return res.json();
  },

  generate: async (): Promise<SolverResponse> => {
    const res = await fetch(`${API_BASE}/generate`, { method: "POST" });
    if (!res.ok) {
      const errData = await res.json().catch(() => null);
      if (errData) return errData;
      throw new Error(`Failed to generate timetable: ${res.statusText}`);
    }
    return res.json();
  },

  getSectionsList: async (): Promise<SectionDTO[]> => {
    const res = await fetch(`${API_BASE}/sections-list`);
    if (!res.ok) throw new Error("Failed to fetch sections list");
    return res.json();
  },

  getTeachersList: async (): Promise<TeacherDTO[]> => {
    const res = await fetch(`${API_BASE}/teachers-list`);
    if (!res.ok) throw new Error("Failed to fetch teachers list");
    return res.json();
  },

  getSections: async (): Promise<Record<string, Record<string, LessonSlot[]>>> => {
    const res = await fetch(`${API_BASE}/sections`);
    if (!res.ok) throw new Error("Failed to fetch sections timetables");
    return res.json();
  },

  getSectionTimetable: async (id: string): Promise<Record<string, LessonSlot[]>> => {
    const res = await fetch(`${API_BASE}/sections/${encodeURIComponent(id)}`);
    if (!res.ok) throw new Error(`Failed to fetch section timetable for ${id}`);
    return res.json();
  },

  getTeachers: async (): Promise<Record<string, Record<string, LessonSlot[]>>> => {
    const res = await fetch(`${API_BASE}/teachers`);
    if (!res.ok) throw new Error("Failed to fetch teachers timetables");
    return res.json();
  },

  getTeacherTimetable: async (code: string): Promise<Record<string, LessonSlot[]>> => {
    const res = await fetch(`${API_BASE}/teachers/${encodeURIComponent(code)}`);
    if (!res.ok) throw new Error(`Failed to fetch teacher timetable for ${code}`);
    return res.json();
  },

  getConflicts: async (): Promise<ConflictReport> => {
    const res = await fetch(`${API_BASE}/conflicts`);
    if (!res.ok) throw new Error("Failed to fetch conflicts");
    return res.json();
  },

  getStatus: async (): Promise<Record<string, unknown>> => {
    const res = await fetch(`${API_BASE}/status`);
    if (!res.ok) throw new Error("Failed to fetch status");
    return res.json();
  },
};
