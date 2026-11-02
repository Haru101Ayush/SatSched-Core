"use client";

import { useState, useEffect } from "react";
import { api, TeacherDTO, LessonSlot } from "@/lib/api";
import { Users, Search, RefreshCw, CheckCircle2 } from "lucide-react";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

export default function TeachersPage() {
  const [teachers, setTeachers] = useState<TeacherDTO[]>([]);
  const [selectedTeacher, setSelectedTeacher] = useState<string>("");
  const [timetables, setTimetables] = useState<Record<string, Record<string, LessonSlot[]>>>({});
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    Promise.all([
      api.getTeachersList().catch(() => []),
      api.getTeachers().catch(() => ({}))
    ]).then(([tList, ttMap]) => {
      setTeachers(tList);
      setTimetables(ttMap);
      if (tList.length > 0) {
        setSelectedTeacher(tList[0].name || tList[0].code);
      }
      setLoading(false);
    });
  }, []);

  const filteredTeachers = teachers.filter((t) =>
    t.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    t.code.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const currentTimetable = timetables[selectedTeacher] || {};

  // Calculate total scheduled periods for selected teacher
  let totalScheduled = 0;
  Object.values(currentTimetable).forEach((daySlots) => {
    totalScheduled += daySlots.length;
  });

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <RefreshCw className="w-8 h-8 animate-spin text-indigo-400" />
        <p className="text-slate-400 text-sm">Loading teacher schedules...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Users className="w-6 h-6 text-indigo-400" />
            Teacher Timetable Schedules
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Faculty weekly workload and cross-section allocation (H1 Double-Booking Prevention)
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search teacher..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="bg-slate-900 border border-slate-800 rounded-lg pl-9 pr-3 py-1.5 text-sm text-white focus:outline-none focus:border-indigo-500 w-48"
            />
          </div>

          <select
            value={selectedTeacher}
            onChange={(e) => setSelectedTeacher(e.target.value)}
            className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-indigo-500 max-w-xs"
          >
            {filteredTeachers.map((t) => (
              <option key={t.code} value={t.name || t.code}>
                {t.name} ({t.code})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Grid */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-4 border-b border-slate-800 bg-slate-900/60 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-bold text-white text-base">{selectedTeacher}</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-medium border border-emerald-500/30 flex items-center gap-1">
              <CheckCircle2 className="w-3 h-3" />
              {totalScheduled} Periods Assigned
            </span>
          </div>
          <span className="text-xs text-slate-400">H1 Hard Constraint: Max 1 Section Per Slot</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[700px]">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/40 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                <th className="py-3 px-4 w-20">Period</th>
                {DAYS.map((d) => (
                  <th key={d} className="py-3 px-4 text-center">
                    {d}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-sm">
              {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((period) => (
                <tr key={period} className="hover:bg-slate-800/20 transition-colors">
                  <td className="py-2.5 px-4 font-mono font-bold text-xs text-slate-400 bg-slate-950/20">
                    P{period}
                  </td>
                  {DAYS.map((day) => {
                    const isFriSat = day === "FRIDAY" || day === "SATURDAY";
                    if (!isFriSat && period > 8) {
                      return (
                        <td key={day} className="py-2.5 px-2 text-center bg-slate-950/50">
                          <span className="text-[11px] text-slate-600 font-medium">—</span>
                        </td>
                      );
                    }

                    const dayLessons = currentTimetable[day] || [];
                    const lesson = dayLessons.find((l) => l.period === period);

                    return (
                      <td key={day} className="py-2 px-2 text-center">
                        {lesson ? (
                          <div className="p-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 text-emerald-200 text-xs font-medium shadow-sm">
                            <div className="font-semibold line-clamp-1">{lesson.section || "Assigned Class"}</div>
                            <div className="text-[10px] text-emerald-300 mt-0.5">{lesson.subject}</div>
                          </div>
                        ) : (
                          <div className="p-2 rounded-lg border border-dashed border-slate-800/70 text-slate-600 text-[11px]">
                            Available
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
