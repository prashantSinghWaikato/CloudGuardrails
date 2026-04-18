import { Link, useLocation, useNavigate } from "react-router-dom";
import { LayoutDashboard, AlertTriangle, Wrench, Database, SlidersHorizontal } from "lucide-react";
import { useEffect } from "react";

const Sidebar = () => {
    const location = useLocation();
    const navigate = useNavigate();

    // ✅ Protect routes (important)
    useEffect(() => {
        const token = localStorage.getItem("token");

        if (!token) {
            navigate("/", { replace: true });
        }
    }, [navigate]);

    // ✅ Better active match (handles nested routes)
    const isActive = (path: string) =>
        location.pathname.startsWith(path);

    const menuClass = (path: string) =>
        `group flex items-center gap-3 rounded-2xl px-4 py-3 transition ${isActive(path)
            ? "bg-[linear-gradient(135deg,rgba(47,125,255,0.22),rgba(88,225,255,0.1))] text-cyan-100 shadow-[0_18px_35px_-24px_rgba(47,125,255,0.95)]"
            : "text-slate-300 hover:bg-white/5 hover:text-white"
        }`;

    return (
        <aside className="sticky top-0 flex h-screen w-[92px] shrink-0 flex-col border-r border-white/10 bg-[linear-gradient(180deg,rgba(7,16,29,0.95),rgba(8,16,28,0.78))] px-3 py-6 backdrop-blur-xl lg:w-[286px] lg:px-5">
            <div className="rounded-[24px] border border-white/10 bg-white/5 p-4 shadow-[0_18px_45px_-30px_rgba(2,8,23,0.9)]">
                <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/5 shadow-[0_18px_36px_-24px_rgba(88,225,255,0.35)]">
                        <img
                            src="/favicon.svg"
                            alt="Guardrails"
                            className="h-7 w-7"
                        />
                    </div>
                    <div className="hidden lg:block">
                        <h1 className="text-lg font-semibold tracking-tight text-white">
                            Guardrails
                        </h1>
                        <p className="text-xs uppercase tracking-[0.24em] text-slate-500">
                            Cloud Security Ops
                        </p>
                    </div>
                </div>
            </div>

            <div className="mt-8 hidden px-2 lg:block">
                <p className="text-[11px] uppercase tracking-[0.28em] text-slate-500">
                    Workspace
                </p>
            </div>

            <nav className="mt-3 space-y-2">

                <Link to="/dashboard" className={menuClass("/dashboard")}>
                    <LayoutDashboard size={18} />
                    <span className="hidden lg:inline">Dashboard</span>
                </Link>

                <Link to="/violations" className={menuClass("/violations")}>
                    <AlertTriangle size={18} />
                    <span className="hidden lg:inline">Violations</span>
                </Link>

                <Link to="/remediations" className={menuClass("/remediations")}>
                    <Wrench size={18} />
                    <span className="hidden lg:inline">Remediations</span>
                </Link>

                <Link to="/accounts" className={menuClass("/accounts")}>
                    <Database size={18} />
                    <span className="hidden lg:inline">Accounts</span>
                </Link>

                <Link to="/rules" className={menuClass("/rules")}>
                    <SlidersHorizontal size={18} />
                    <span className="hidden lg:inline">Rules</span>
                </Link>

            </nav>

            <div className="flex-1" />

            <div className="hidden rounded-[24px] border border-white/10 bg-white/5 p-4 lg:block">
                <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                    Platform Status
                </p>
                <p className="mt-3 text-sm font-medium text-white">
                    Guardrails is ready for live cloud posture monitoring.
                </p>
                <p className="mt-2 text-sm leading-6 text-slate-400">
                    Connect accounts, review rule behavior, and validate remediations from a single console.
                </p>
            </div>

            <div className="mt-4 hidden px-2 text-xs text-slate-500 lg:block">
                Cloud Guardrails v1
            </div>
        </aside>
    );
};

export default Sidebar;
