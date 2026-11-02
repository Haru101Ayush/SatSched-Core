"use client";

import { useState, useEffect } from "react";
import { api, ConflictReport } from "@/lib/api";
import { AlertTriangle, AlertCircle, Search, Filter, RefreshCw } from "lucide-react";

export default function ConflictsPage() {
  const [conflicts, setConflicts] = useState<ConflictReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"data" | "scheduling">("data");
  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    api.getConflicts()
      .then((data) => {
        setConflicts(data);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <RefreshCw className="w-8 h-8 animate-spin text-indigo-400" />
        <p className="text-slate-400 text-sm">Loading conflict reports...</p>
      </div>
    );
  }

  const dataConflicts = conflicts?.dataConflicts || [];
  const schedulingConflicts = conflicts?.schedulingConflicts || [];

  const conflictTypes = ["ALL", ...Array.from(new Set(dataConflicts.map((c) => c.type)))];

  const filteredDataConflicts = dataConflicts.filter((c) => {
    const matchesType = typeFilter === "ALL" || c.type === typeFilter;
    const matchesSearch =
      c.section.toLowerCase().includes(searchTerm.toLowerCase()) ||
      c.subject.toLowerCase().includes(searchTerm.toLowerCase()) ||
      c.teacherCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      c.message.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesType && matchesSearch;
  });

  const filteredSchedulingConflicts = schedulingConflicts.filter((c) =>
    c.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-800 pb-5">
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <AlertTriangle className="w-6 h-6 text-amber-400" />
          Conflict & Data Gap Audit
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          Full transparency report for UNASSIGNED-TT rows, zero workloads, and solver capacity vs demand explanations
        </p>
      </div>

      {/* Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-2 p-1 bg-slate-900 border border-slate-800 rounded-xl w-fit">
          <button
            onClick={() => setActiveTab("data")}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
              activeTab === "data"
                ? "bg-indigo-600 text-white shadow-sm"
                : "text-slate-400 hover:text-white"
            }`}
          >
            Data Quality Gaps ({dataConflicts.length})
          </button>
          <button
            onClick={() => setActiveTab("scheduling")}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
              activeTab === "scheduling"
                ? "bg-indigo-600 text-white shadow-sm"
                : "text-slate-400 hover:text-white"
            }`}
          >
            Scheduling Conflicts ({schedulingConflicts.length})
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {activeTab === "data" && (
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-slate-400" />
              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-indigo-500"
              >
                {conflictTypes.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search in conflicts..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="bg-slate-900 border border-slate-800 rounded-lg pl-9 pr-3 py-1.5 text-xs text-white focus:outline-none focus:border-indigo-500 w-52"
            />
          </div>
        </div>
      </div>

      {/* Content */}
      {activeTab === "data" ? (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-800 bg-slate-950/40 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Section</th>
                  <th className="py-3 px-4">Subject</th>
                  <th className="py-3 px-4">Teacher</th>
                  <th className="py-3 px-4">Audit Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-xs">
                {filteredDataConflicts.length > 0 ? (
                  filteredDataConflicts.map((c, i) => (
                    <tr key={i} className="hover:bg-slate-800/20 transition-colors">
                      <td className="py-2.5 px-4 font-mono font-medium">
                        <span
                          className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                            c.type === "UNASSIGNED_TEACHER"
                              ? "bg-amber-500/20 text-amber-400 border border-amber-500/30"
                              : c.type === "ZERO_WORKLOAD"
                              ? "bg-slate-800 text-slate-400 border border-slate-700"
                              : "bg-rose-500/20 text-rose-400 border border-rose-500/30"
                          }`}
                        >
                          {c.type}
                        </span>
                      </td>
                      <td className="py-2.5 px-4 font-medium text-white">{c.section || "—"}</td>
                      <td className="py-2.5 px-4 text-slate-300">{c.subject || "—"}</td>
                      <td className="py-2.5 px-4 font-mono text-slate-400">{c.teacherCode || "—"}</td>
                      <td className="py-2.5 px-4 text-slate-300">{c.message}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-slate-500 text-sm">
                      No matching data quality issues found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredSchedulingConflicts.length > 0 ? (
            filteredSchedulingConflicts.map((c, i) => (
              <div
                key={i}
                className="p-4 bg-slate-900 border border-amber-500/30 rounded-xl text-xs sm:text-sm text-amber-200 flex items-start gap-3 shadow-md"
              >
                <AlertCircle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                <div className="font-mono">{c}</div>
              </div>
            ))
          ) : (
            <div className="p-8 bg-slate-900 border border-slate-800 rounded-xl text-center text-slate-500 text-sm">
              No scheduling conflicts recorded. Run the solver to detect constraint issues.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
