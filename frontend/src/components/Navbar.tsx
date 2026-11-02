"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Calendar, Users, AlertTriangle, Home, Clock } from "lucide-react";

export default function Navbar() {
  const pathname = usePathname();

  const navItems = [
    { href: "/", label: "Dashboard", icon: Home },
    { href: "/sections", label: "Section Timetables", icon: Calendar },
    { href: "/teachers", label: "Teacher Schedules", icon: Users },
    { href: "/conflicts", label: "Conflicts & Data Gaps", icon: AlertTriangle },
  ];

  return (
    <header className="bg-slate-900 border-b border-slate-800 text-white sticky top-0 z-50 shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center gap-3">
            <div className="bg-indigo-600 p-2 rounded-lg text-white shadow-sm">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <span className="font-bold text-lg tracking-tight bg-gradient-to-r from-white to-slate-300 bg-clip-text text-transparent">
                RDPL Timetable Solver
              </span>
              <span className="text-xs text-indigo-400 block -mt-1 font-medium">Google OR-Tools CP-SAT</span>
            </div>
          </div>

          <nav className="flex items-center gap-1 sm:gap-2">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-2 px-3.5 py-2 rounded-md text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-slate-300 hover:bg-slate-800 hover:text-white"
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>
        </div>
      </div>
    </header>
  );
}
