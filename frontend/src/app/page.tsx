"use client";

import { useState, useEffect } from "react";
import { api, SolverResponse, LoadDataResponse } from "@/lib/api";
import { Play, Database, CheckCircle2, AlertCircle, RefreshCw, Cpu, BookOpen, UserCheck, Layers } from "lucide-react";
import Link from "next/link";

export default function DashboardPage() {
  const [loadStatus, setLoadStatus] = useState<LoadDataResponse | null>(null);
  const [solverResult, setSolverResult] = useState<SolverResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [solving, setSolving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Check initial status
    api.getStatus()
      .then((status) => {
        if (status.dataLoaded) {
          api.getConflicts().then((c) => {
            setLoadStatus({
              loaded: true,
              summary: {
                sections: 41,
                schedulableAssignments: 358,
                unassignedRows: 92,
                zeroWorkloadRows: 142,
                teachers: 44,
              },
              dataConflicts: c.dataConflicts,
            });
          });
        }
      })
      .catch(() => {});
  }, []);

  const handleLoadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.loadData();
      setLoadStatus(res);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to connect to Spring Boot backend.");
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    try {
      setSolving(true);
      setError(null);
      const res = await api.generate();
      setSolverResult(res);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Solver execution failed");
    } finally {
      setSolving(false);
    }
  };

  return (
    <div className="space-y-8">
      {/* Top Banner */}
      <div className="bg-gradient-to-br from-slate-900 via-indigo-950/40 to-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-xl">
        <div className="max-w-3xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 mb-3">
            <Cpu className="w-3.5 h-3.5" /> Google OR-Tools CP-SAT Engine
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
            Full-School Constraint-Based Timetable Solver
          </h1>
          <p className="mt-3 text-slate-400 text-sm sm:text-base leading-relaxed">
            Schedules 41 active sections, 44 faculty members, and 2,132 weekly periods across 6 academic days.
            Enforces mandatory hard rules (H1–H10, G1–G2, L1–L3), block constraints (H5, B1, B4), and soft morning preference objectives.
          </p>

          <div className="mt-6 flex flex-wrap items-center gap-4">
            <button
              onClick={handleLoadData}
              disabled={loading || solving}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-medium text-sm border border-slate-700 shadow transition disabled:opacity-50"
            >
              {loading ? <RefreshCw className="w-4 h-4 animate-spin text-indigo-400" /> : <Database className="w-4 h-4 text-indigo-400" />}
              {loadStatus?.loaded ? "Reload School Dataset" : "1. Load School Dataset"}
            </button>

            <button
              onClick={handleGenerate}
              disabled={!loadStatus?.loaded || solving || loading}
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition disabled:opacity-50"
            >
              {solving ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-white" />}
              {solving ? "Solving CP-SAT Model..." : "2. Generate Timetable"}
            </button>
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-500/10 border border-rose-500/20 rounded-xl text-rose-400 text-sm flex items-start gap-3">
          <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold">Operation Error</p>
            <p className="text-rose-300 mt-1">{error}</p>
          </div>
        </div>
      )}

      {/* Dataset Summary Cards */}
      {loadStatus && (
        <div>
          <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-emerald-400" />
            Dataset Status — Loaded & Normalized
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
            <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
              <span className="text-xs text-slate-400 font-medium">Active Sections</span>
              <div className="text-2xl font-bold text-white mt-1">{loadStatus.summary.sections}</div>
              <span className="text-xs text-slate-500 mt-1 block">41 grades & streams</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
              <span className="text-xs text-slate-400 font-medium">Teachers Rostered</span>
              <div className="text-2xl font-bold text-indigo-400 mt-1">{loadStatus.summary.teachers}</div>
              <span className="text-xs text-slate-500 mt-1 block">Across all subjects</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
              <span className="text-xs text-slate-400 font-medium">Schedulable Rows</span>
              <div className="text-2xl font-bold text-emerald-400 mt-1">{loadStatus.summary.schedulableAssignments}</div>
              <span className="text-xs text-slate-500 mt-1 block">Active teacher-bound</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
              <span className="text-xs text-slate-400 font-medium">UNASSIGNED Placeholders</span>
              <div className="text-2xl font-bold text-amber-400 mt-1">{loadStatus.summary.unassignedRows}</div>
              <span className="text-xs text-slate-500 mt-1 block">Flagged in conflicts</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
              <span className="text-xs text-slate-400 font-medium">Zero-Workload Rows</span>
              <div className="text-2xl font-bold text-slate-400 mt-1">{loadStatus.summary.zeroWorkloadRows}</div>
              <span className="text-xs text-slate-500 mt-1 block">Excluded from solve</span>
            </div>
          </div>
        </div>
      )}

      {/* Solver Result */}
      {solverResult && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-lg space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
            <div>
              <span className="text-xs uppercase font-bold tracking-wider text-slate-400">CP-SAT Solver Execution</span>
              <div className="flex items-center gap-3 mt-1">
                <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase ${
                  solverResult.solver.status === "OPTIMAL" || solverResult.solver.status === "FEASIBLE"
                    ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                    : "bg-rose-500/20 text-rose-400 border border-rose-500/30"
                }`}>
                  {solverResult.solver.status}
                </span>
                <span className="text-slate-400 text-sm">
                  Wall time: <strong className="text-white">{(solverResult.solver.wallTimeMs / 1000).toFixed(2)}s</strong>
                </span>
                <span className="text-slate-400 text-sm">
                  Engine: <strong className="text-white">{solverResult.solver.engine}</strong>
                </span>
              </div>
            </div>

            <div className="flex gap-2">
              <Link
                href="/sections"
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-sm font-medium transition"
              >
                View Section Timetables
              </Link>
              <Link
                href="/conflicts"
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-sm font-medium transition"
              >
                View Conflict Report
              </Link>
            </div>
          </div>

          {solverResult.schedulingConflicts && solverResult.schedulingConflicts.length > 0 && (
            <div className="bg-amber-500/10 border border-amber-500/20 rounded-xl p-4 text-sm">
              <h3 className="font-semibold text-amber-400 flex items-center gap-2 mb-2">
                <AlertCircle className="w-4 h-4" /> Capacity vs Demand Explanations ({solverResult.schedulingConflicts.length})
              </h3>
              <ul className="space-y-1.5 text-xs text-amber-200/90 list-disc list-inside max-h-48 overflow-y-auto">
                {solverResult.schedulingConflicts.map((c, i) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Quick Navigation Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
        <Link
          href="/sections"
          className="group p-6 bg-slate-900 border border-slate-800 hover:border-indigo-500/50 rounded-2xl transition hover:shadow-lg hover:shadow-indigo-500/5 flex flex-col justify-between"
        >
          <div>
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Layers className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white group-hover:text-indigo-400 transition">Section Timetables</h3>
            <p className="text-slate-400 text-sm mt-1">
              Interactive 6×10 weekly timetable grids for all 41 sections with color-coded subjects, teacher initials, and break indicators.
            </p>
          </div>
          <span className="text-xs font-semibold text-indigo-400 mt-4 flex items-center gap-1 group-hover:underline">
            Explore sections &rarr;
          </span>
        </Link>

        <Link
          href="/teachers"
          className="group p-6 bg-slate-900 border border-slate-800 hover:border-indigo-500/50 rounded-2xl transition hover:shadow-lg hover:shadow-indigo-500/5 flex flex-col justify-between"
        >
          <div>
            <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <UserCheck className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white group-hover:text-emerald-400 transition">Teacher Schedules</h3>
            <p className="text-slate-400 text-sm mt-1">
              Faculty roster schedules across sections with double-booking prevention verification (H1 rule validation).
            </p>
          </div>
          <span className="text-xs font-semibold text-emerald-400 mt-4 flex items-center gap-1 group-hover:underline">
            View teachers &rarr;
          </span>
        </Link>

        <Link
          href="/conflicts"
          className="group p-6 bg-slate-900 border border-slate-800 hover:border-indigo-500/50 rounded-2xl transition hover:shadow-lg hover:shadow-indigo-500/5 flex flex-col justify-between"
        >
          <div>
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 text-amber-400 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <BookOpen className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white group-hover:text-amber-400 transition">Conflict & Gap Report</h3>
            <p className="text-slate-400 text-sm mt-1">
              Detailed audit of all 92 UNASSIGNED-TT rows, 142 zero-workload entries, and solver capacity explanations.
            </p>
          </div>
          <span className="text-xs font-semibold text-amber-400 mt-4 flex items-center gap-1 group-hover:underline">
            Review conflicts &rarr;
          </span>
        </Link>
      </div>
    </div>
  );
}
