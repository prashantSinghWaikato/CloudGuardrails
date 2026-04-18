import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";

import RemediationsPage from "./pages/Remediations";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import DashboardCards from "./components/DashboardCards";
import ViolationTable from "./components/ViolationTable";
import SeverityChart from "./components/charts/SeverityChart";
import ComplianceChart from "./components/charts/ComplianceChart";
import TopRulesChart from "./components/charts/TopRulesChart";
import ViolationsPage from "./pages/Violations";
import Accounts from "./pages/Accounts";
import RulesPage from "./pages/Rules";
import { fetchViolations } from "./api/ViolationApi";
import { connectWebSocket } from "./api/Websocket";
import type { Violation } from "./types";

import Signup from "./pages/Signup";
import Login from "./pages/Login";


function DashboardHome() {
  const [dashboardViolations, setDashboardViolations] = useState<Violation[]>([]);
  const [dashboardLoading, setDashboardLoading] = useState(true);

  useEffect(() => {
    let mounted = true;

    const loadDashboardViolations = async () => {
      try {
        if (mounted) {
          setDashboardLoading(true);
        }

        const response = await fetchViolations(0, 100);

        if (!mounted) {
          return;
        }

        setDashboardViolations(response.content ?? []);
      } catch (error) {
        console.error("Dashboard violations error:", error);
      } finally {
        if (mounted) {
          setDashboardLoading(false);
        }
      }
    };

    void loadDashboardViolations();

    const disconnect = connectWebSocket((incomingViolation) => {
      setDashboardLoading(false);
      setDashboardViolations((current) => {
        const next = [...current];
        const existingIndex = next.findIndex((item) => item.id === incomingViolation.id);

        if (existingIndex >= 0) {
          next[existingIndex] = incomingViolation;
        } else {
          next.unshift(incomingViolation);
        }

        next.sort((a, b) => b.id - a.id);
        return next.slice(0, 100);
      });
    });

    return () => {
      mounted = false;
      disconnect();
    };
  }, []);

  const recentViolations = useMemo(
    () => [...dashboardViolations].sort((a, b) => b.id - a.id).slice(0, 5),
    [dashboardViolations]
  );

  return (
    <main className="mx-auto flex w-full max-w-[1380px] flex-col gap-8 px-6 py-8">
      <section className="relative overflow-hidden rounded-[28px] border border-white/10 bg-[linear-gradient(135deg,rgba(8,20,37,0.96),rgba(17,36,58,0.92))] px-8 py-8 shadow-[0_32px_80px_-36px_rgba(2,8,23,0.95)]">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(88,225,255,0.18),transparent_28%),radial-gradient(circle_at_bottom_left,rgba(47,125,255,0.22),transparent_36%)]" />
        <div className="relative flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl space-y-4">
            <div className="inline-flex items-center gap-2 rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-xs font-medium uppercase tracking-[0.24em] text-cyan-200">
              Live Security Posture
            </div>
            <div className="space-y-3">
              <h1 className="max-w-xl text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                Detect drift early and move from violation to verified remediation.
              </h1>
              <p className="max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
                Monitor critical exposures across onboarded cloud accounts, track remediation execution,
                and keep your guardrails visible in one operational workspace.
              </p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-white/5 px-4 py-4 backdrop-blur-sm">
              <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Coverage</p>
              <p className="mt-3 text-2xl font-semibold text-white">AWS</p>
              <p className="mt-1 text-sm text-slate-400">Rule-driven cloud account monitoring</p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 px-4 py-4 backdrop-blur-sm">
              <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Realtime</p>
              <p className="mt-3 text-2xl font-semibold text-white">STOMP</p>
              <p className="mt-1 text-sm text-slate-400">Push updates for violations and remediations</p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 px-4 py-4 backdrop-blur-sm">
              <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Workflow</p>
              <p className="mt-3 text-2xl font-semibold text-white">Verify</p>
              <p className="mt-1 text-sm text-slate-400">Track execution, validation, and fixed status</p>
            </div>
          </div>
        </div>
      </section>

      <DashboardCards violations={dashboardViolations} />

      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.26em] text-slate-500">Overview</p>
          <h2 className="mt-2 text-2xl font-semibold text-slate-100">
            Exposure and remediation analytics
          </h2>
        </div>
        <p className="max-w-md text-right text-sm text-slate-400">
          Watch severity distribution, policy drift signals, and the rules generating the most operational noise.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.25fr_0.95fr_1fr]">
        <div className="min-h-[320px]">
          <ComplianceChart violations={dashboardViolations} loading={dashboardLoading} />
        </div>

        <div className="min-h-[320px]">
          <SeverityChart violations={dashboardViolations} loading={dashboardLoading} />
        </div>

        <div className="min-h-[320px]">
          <TopRulesChart violations={dashboardViolations} loading={dashboardLoading} />
        </div>
      </div>

      <section className="space-y-4">
        <div className="flex items-end justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.26em] text-slate-500">Operations</p>
            <h2 className="mt-2 text-2xl font-semibold text-slate-100">
              Violations
            </h2>
          </div>
          <p className="max-w-md text-right text-sm text-slate-400">
            The latest findings across your visible cloud accounts, ready for triage or remediation.
          </p>
        </div>

        <ViolationTable data={recentViolations} loadingOverride={dashboardLoading} />
      </section>
    </main>
  );
}


function DashboardLayout() {
  return (
    <div className="flex min-h-screen bg-transparent text-white">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_top,rgba(47,125,255,0.18),transparent_24%)] opacity-70" />

      <Sidebar />

      <div className="flex min-h-screen min-w-0 flex-1 flex-col overflow-y-auto">
        <Topbar />
        <Outlet />
      </div>
    </div>
  );
}


// 🔐 PROTECTED ROUTE
function ProtectedRoute({ isLoggedIn }: { isLoggedIn: boolean }) {
  return isLoggedIn ? <Outlet /> : <Navigate to="/" replace />;
}


function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem("token")
  );

  return (
    <BrowserRouter>
      <Routes>

        {/* 🔐 LOGIN */}
        <Route
          path="/"
          element={
            isLoggedIn
              ? <Navigate to="/dashboard" replace />
              : <Login setIsLoggedIn={setIsLoggedIn} />
          }
        />

        {/* SIGNUP */}
        <Route path="/signup" element={<Signup setIsLoggedIn={setIsLoggedIn} />} />

        {/* 🔒 PROTECTED ROUTES */}
        <Route element={<ProtectedRoute isLoggedIn={isLoggedIn} />}>

          <Route element={<DashboardLayout />}>

            <Route path="/dashboard" element={<DashboardHome />} />

            <Route path="/violations" element={
              <div className="p-6">
                <ViolationsPage />
              </div>
            } />

            <Route path="/remediations" element={
              <div className="p-6">
                <RemediationsPage />
              </div>
            } />

            <Route path="/accounts" element={<Accounts />} />

            <Route path="/rules" element={<RulesPage />} />

          </Route>

        </Route>

        {/* ❌ FALLBACK */}
        <Route path="*" element={<Navigate to="/" />} />

      </Routes>
    </BrowserRouter>
  );
}

export default App;
