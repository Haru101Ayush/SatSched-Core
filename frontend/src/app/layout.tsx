import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import Navbar from "@/components/Navbar";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "RDPL Timetable Solver — OR-Tools CP-SAT",
  description: "Full-school constraint-based timetable scheduler using Google OR-Tools CP-SAT and Spring Boot",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${inter.className} min-h-screen bg-slate-950 text-slate-100 flex flex-col`}>
        <Navbar />
        <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
          {children}
        </main>
        <footer className="border-t border-slate-800/80 py-4 text-center text-xs text-slate-500 bg-slate-900/50">
          RDPL Spring Boot Developer Assessment · Full-School CP-SAT Timetable Engine · 41 Sections · 2,132 Weekly Slots
        </footer>
      </body>
    </html>
  );
}
