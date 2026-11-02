"use client";

import { useState, useEffect } from "react";
import { api, SectionDTO, LessonSlot } from "@/lib/api";
import { Calendar, Search, RefreshCw } from "lucide-react";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

export default function SectionsPage() {
  const [sections, setSections] = useState<SectionDTO[]>([]);
  const [selectedSection, setSelectedSection] = useState<string>("");
  const [timetables, setTimetables] = useState<Record<string, Record<string, LessonSlot[]>>>({});
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    Promise.all([
      api.getSectionsList().catch(() => []),
      api.getSections().catch(() => ({}))
    ]).then(([secList, ttMap]) => {
      setSections(secList);
      setTimetables(ttMap);
      if (secList.length > 0) {
        setSelectedSection(secList[0].displayName);
      }
      setLoading(false);
    });
  }, []);

  const filteredSections = sections.filter((s) =>
    s.displayName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    s.grade.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const currentTimetable = timetables[selectedSection] || {};

  const getSubjectColor = (subject: string) => {
    const s = subject.toLowerCase();
    if (s.includes("math")) return "bg-blue-500/15 text-blue-300 border-blue-500/30";
    if (s.includes("english")) return "bg-indigo-500/15 text-indigo-300 border-indigo-500/30";
    if (s.includes("science") || s.includes("physics") || s.includes("chem") || s.includes("bio"))
      return "bg-emerald-500/15 text-emerald-300 border-emerald-500/30";
    if (s.includes("hindi")) return "bg-amber-500/15 text-amber-300 border-amber-500/30";
    if (s.includes("history") || s.includes("geo") || s.includes("social"))
      return "bg-orange-500/15 text-orange-300 border-orange-500/30";
    if (s.includes("games") || s.includes("pe") || s.includes("physical"))
      return "bg-rose-500/15 text-rose-300 border-rose-500/30";
    if (s.includes("computer")) return "bg-cyan-500/15 text-cyan-300 border-cyan-500/30";
    if (s.includes("art") || s.includes("karate") || s.includes("library"))
      return "bg-purple-500/15 text-purple-300 border-purple-500/30";
    return "bg-slate-800 text-slate-300 border-slate-700";
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <RefreshCw className="w-8 h-8 animate-spin text-indigo-400" />
        <p className="text-slate-400 text-sm">Loading section timetables...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Calendar className="w-6 h-6 text-indigo-400" />
            Section Timetables
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Weekly 6-day teaching grid across 52 scheduled slots
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search section..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="bg-slate-900 border border-slate-800 rounded-lg pl-9 pr-3 py-1.5 text-sm text-white focus:outline-none focus:border-indigo-500 w-48"
            />
          </div>

          <select
            value={selectedSection}
            onChange={(e) => setSelectedSection(e.target.value)}
            className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-indigo-500"
          >
            {filteredSections.map((s) => (
              <option key={s.id} value={s.displayName}>
                {s.displayName}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Grid */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-4 border-b border-slate-800 bg-slate-900/60 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-bold text-white text-base">{selectedSection}</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 font-medium border border-indigo-500/30">
              52 Slots / Week
            </span>
          </div>
          <span className="text-xs text-slate-400">Mon–Thu: 8 Periods | Fri–Sat: 10 Periods</span>
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
                          <div
                            className={`p-2 rounded-lg border text-xs font-medium transition hover:scale-105 shadow-sm ${getSubjectColor(
                              lesson.subject
                            )}`}
                          >
                            <div className="font-semibold line-clamp-1">{lesson.subject}</div>
                            <div className="text-[10px] opacity-80 mt-0.5 font-mono">
                              {lesson.teacherName || lesson.teacherCode}
                            </div>
                          </div>
                        ) : (
                          <div className="p-2 rounded-lg border border-dashed border-slate-800 text-slate-600 text-[11px]">
                            Free / Unassigned
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
