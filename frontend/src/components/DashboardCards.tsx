import { useCallback, useEffect, useState } from "react";
import { fetchAccounts } from "../api/accountApi";
import type { Violation } from "../types";

type Stats = {
    protectedAccounts: number;
    violations: number;
    open: number;
    critical: number;
};

type Props = {
    violations: Violation[];
};

const DashboardCards = ({ violations }: Props) => {
    const [stats, setStats] = useState<Stats>({
        protectedAccounts: 0,
        violations: 0,
        open: 0,
        critical: 0,
    });

    const [loading, setLoading] = useState(true);

    const loadStats = useCallback(async () => {
        try {
            setLoading(true);
            const accounts = await fetchAccounts();
            const total = violations.length;
            const open = violations.filter((item) => item.status === "OPEN").length;
            const critical = violations.filter(
                (item) => item.severity === "CRITICAL"
            ).length;

            setStats({
                protectedAccounts: accounts.length,
                violations: total,
                open,
                critical,
            });

        } catch (e) {
            console.error("Dashboard stats error:", e);
        } finally {
            setLoading(false);
        }
    }, [violations]);

    useEffect(() => {
        void loadStats();

        const handleAccountsUpdated = () => {
            void loadStats();
        };

        window.addEventListener("accountsUpdated", handleAccountsUpdated);
        return () => {
            window.removeEventListener("accountsUpdated", handleAccountsUpdated);
        };
    }, [loadStats]);

    if (loading) {
        return (
            <div className="text-center text-gray-400 py-10">
                Loading dashboard...
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
            <Card title="Protected Accounts" value={stats.protectedAccounts} />
            <Card title="Violations" value={stats.violations} />
            <Card title="Open" value={stats.open} />
            <Card title="Critical" value={stats.critical} />
        </div>
    );
};

// ✅ Better typing
type CardProps = {
    title: string;
    value: number;
};

const Card = ({ title, value }: CardProps) => (
    <div className="group relative overflow-hidden rounded-[24px] border border-white/10 bg-[linear-gradient(180deg,rgba(15,26,43,0.94),rgba(10,20,33,0.82))] p-5 shadow-[0_22px_60px_-34px_rgba(2,8,23,0.95)] transition duration-200 hover:-translate-y-0.5 hover:border-cyan-400/20">
        <div className="absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(88,225,255,0.5),transparent)] opacity-0 transition group-hover:opacity-100" />
        <p className="text-[11px] uppercase tracking-[0.26em] text-slate-500">{title}</p>
        <p className="mt-5 text-4xl font-semibold tracking-tight text-white">{value}</p>
        <p className="mt-3 text-sm text-slate-400">
            {title === "Protected Accounts"
                ? "Cloud accounts currently onboarded and visible in this workspace."
                : title === "Critical"
                ? "Highest-priority policy breaches requiring immediate action."
                : title === "Open"
                    ? "Findings still awaiting remediation or verification."
                    : "Current tenant-scoped findings visible to this workspace."}
        </p>
    </div>
);

export default DashboardCards;
