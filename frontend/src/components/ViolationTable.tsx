import { useState, useEffect, useCallback } from "react";
import ViolationDrawer from "./ViolationDrawer";
import { connectWebSocket } from "../api/Websocket";
import {
    fetchRecentViolations,
    fetchFilteredViolations,
    fetchViolations,
    searchViolations,
} from "../api/ViolationApi";
import { apiFetch } from "../api/api";
import type { Severity, Violation } from "../types";

type Props = {
    fullView?: boolean;
    data?: Violation[];
    loadingOverride?: boolean;
};

const PAGE_SIZE = 10;
const ALL_OPTION = "ALL";
const SEVERITY_OPTIONS: Array<Severity | typeof ALL_OPTION> = [
    ALL_OPTION,
    "LOW",
    "MEDIUM",
    "HIGH",
    "CRITICAL",
];
const STATUS_OPTIONS = [ALL_OPTION, "OPEN", "FIXED"];

const ViolationTable = ({ fullView = false, data, loadingOverride }: Props) => {
    const [violations, setViolations] = useState<Violation[]>([]);
    const [loading, setLoading] = useState(true);

    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const [search, setSearch] = useState("");
    const [severity, setSeverity] = useState<typeof ALL_OPTION | Severity>(ALL_OPTION);
    const [status, setStatus] = useState(ALL_OPTION);

    const [selected, setSelected] = useState<Violation | null>(null);
    const [open, setOpen] = useState(false);
    const isExternalData = !fullView && Array.isArray(data);

    const getSeverityColor = (sev: string) => {
        switch (sev) {
            case "CRITICAL":
                return "bg-red-500/10 text-red-400 border border-red-500/20";
            case "HIGH":
                return "bg-orange-500/10 text-orange-400 border border-orange-500/20";
            case "MEDIUM":
                return "bg-yellow-500/10 text-yellow-400 border border-yellow-500/20";
            default:
                return "bg-green-500/10 text-green-400 border border-green-500/20";
        }
    };

    const loadData = useCallback(async () => {
        if (isExternalData) {
            return;
        }

        setLoading(true);

        try {
            if (fullView) {
                const trimmedSearch = search.trim();
                const severityFilter = severity === ALL_OPTION ? undefined : severity;
                const statusFilter = status === ALL_OPTION ? undefined : status;

                const data = trimmedSearch
                    ? await searchViolations(trimmedSearch, page, PAGE_SIZE)
                    : severityFilter || statusFilter
                        ? await fetchFilteredViolations(
                            page,
                            PAGE_SIZE,
                            severityFilter,
                            statusFilter
                        )
                        : await fetchViolations(page, PAGE_SIZE);

                setViolations(data.content);
                setTotalPages(data.totalPages);
            } else {
                const data = await fetchRecentViolations();
                setViolations(data);
            }

        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [fullView, isExternalData, page, search, severity, status]);

    useEffect(() => {
        void loadData();
    }, [loadData]);

    useEffect(() => {
        if (!isExternalData) {
            return;
        }

        setViolations(data);
        setLoading(loadingOverride ?? false);
    }, [data, isExternalData, loadingOverride]);

    useEffect(() => {
        if (!fullView) {
            return;
        }

        setPage(0);
    }, [fullView, search, severity, status]);

    useEffect(() => {
        if (isExternalData) {
            return;
        }

        const disconnect = connectWebSocket(
            (newViolation) => {
                setViolations((prev) => {
                    const existingIndex = prev.findIndex((item) => item.id === newViolation.id);

                    if (existingIndex >= 0) {
                        const next = [...prev];
                        next[existingIndex] = newViolation;
                        return next;
                    }

                    if (fullView) {
                        return prev;
                    }

                    const updated = [newViolation, ...prev];
                    return updated.slice(0, 5);
                });
            },
            () => { }
        );

        return () => {
            disconnect();
        };
    }, [fullView, isExternalData]);

    // ✅ REMEDIATION
    const triggerRemediation = async (id: number) => {
        try {
            await apiFetch(`/violations/${id}/remediate`, {
                method: "POST",
            });
        } catch (error) {
            console.error("Remediation trigger failed", error);
        }
    };

    const getStatusClass = (currentStatus: string) => {
        switch (currentStatus) {
            case "FIXED":
                return "bg-emerald-500/10 text-emerald-300 border border-emerald-500/20";
            case "OPEN":
                return "bg-blue-500/10 text-blue-300 border border-blue-500/20";
            default:
                return "bg-slate-500/10 text-slate-300 border border-slate-500/20";
        }
    };

    return (
        <div className="bg-white/5 backdrop-blur-md border border-white/10 rounded-xl shadow-lg overflow-hidden w-full">

            <div className="p-6 space-y-4">

                <div className="flex justify-between items-center">
                    <h2 className="text-lg font-semibold text-gray-200">
                        {fullView ? "All Violations" : "Recent Violations"}
                    </h2>

                    {fullView && (
                        <div className="flex items-center gap-3">
                            <select
                                value={severity}
                                onChange={(e) => {
                                    setSeverity(e.target.value as typeof ALL_OPTION | Severity);
                                    setSearch("");
                                }}
                                className="bg-gray-800 border border-gray-700 px-3 py-1.5 rounded text-sm"
                            >
                                {SEVERITY_OPTIONS.map((option) => (
                                    <option key={option} value={option}>
                                        {option === ALL_OPTION ? "All Severities" : option}
                                    </option>
                                ))}
                            </select>

                            <select
                                value={status}
                                onChange={(e) => {
                                    setStatus(e.target.value);
                                    setSearch("");
                                }}
                                className="bg-gray-800 border border-gray-700 px-3 py-1.5 rounded text-sm"
                            >
                                {STATUS_OPTIONS.map((option) => (
                                    <option key={option} value={option}>
                                        {option === ALL_OPTION ? "All Statuses" : option}
                                    </option>
                                ))}
                            </select>

                            <input
                                value={search}
                                onChange={(e) => {
                                    setSearch(e.target.value);
                                    setSeverity(ALL_OPTION);
                                    setStatus(ALL_OPTION);
                                }}
                                placeholder="Search resource or rule"
                                className="bg-gray-800 border border-gray-700 px-3 py-1.5 rounded text-sm"
                            />
                        </div>
                    )}
                </div>

                <div className="w-full overflow-hidden rounded-lg">

                    <table className="w-full table-fixed text-sm">

                        <thead className="text-gray-400 text-xs uppercase border-b border-gray-800">
                            <tr>
                                <th className="text-left py-3 w-[25%]">Rule</th>
                                <th className="text-left w-[20%]">Resource</th>
                                <th className="text-left w-[20%]">Account</th>
                                <th className="w-[15%] text-center">Severity</th>
                                <th className="w-[15%] text-center">Status</th>
                                <th className="w-[10%] text-center">Fix</th>
                            </tr>
                        </thead>

                        <tbody>
                            {loading ? (
                                <tr>
                                    <td colSpan={6} className="text-center py-10">
                                        Loading...
                                    </td>
                                </tr>
                            ) : violations.map((v, i) => (
                                (() => {
                                    const canFix = v.status === "OPEN";

                                    return (
                                        <tr
                                            key={v.id}
                                            onClick={() => {
                                                setSelected(v);
                                                setOpen(true);
                                            }}
                                            className={`border-b border-gray-800 cursor-pointer
                                    ${i % 2 ? "bg-gray-900/40" : ""}
                                    hover:bg-blue-500/10`}
                                        >
                                            <td className="py-3 truncate">{v.ruleName}</td>
                                            <td className="truncate">{v.resourceId}</td>
                                            <td className="truncate">{v.accountId}</td>

                                            <td className="text-center">
                                                <span className={`px-2 py-1 text-xs rounded ${getSeverityColor(v.severity)}`}>
                                                    {v.severity}
                                                </span>
                                            </td>

                                            <td className="text-center">
                                                <span className={`px-2 py-1 text-xs rounded border ${getStatusClass(v.status)}`}>
                                                    {v.status}
                                                </span>
                                            </td>

                                            <td className="text-center">
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        if (!canFix) {
                                                            return;
                                                        }
                                                        triggerRemediation(v.id);
                                                    }}
                                                    disabled={!canFix}
                                                    className={`px-3 py-1 text-xs rounded transition ${
                                                        canFix
                                                            ? "bg-purple-600 hover:bg-purple-700 text-white"
                                                            : "bg-slate-700/60 text-slate-400 cursor-not-allowed"
                                                    }`}
                                                >
                                                    {canFix ? "Fix" : "Fixed"}
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })()
                            ))}
                        </tbody>
                    </table>
                </div>

                {fullView && (
                    <div className="flex justify-between items-center pt-2">
                        <button
                            disabled={page === 0}
                            onClick={() => setPage((p) => p - 1)}
                            className="px-3 py-1 bg-gray-800 rounded disabled:opacity-40"
                        >
                            Prev
                        </button>

                        <span className="text-sm text-gray-400">
                            Page {totalPages === 0 ? 0 : page + 1} / {totalPages}
                        </span>

                        <button
                            disabled={page >= totalPages - 1}
                            onClick={() => setPage((p) => p + 1)}
                            className="px-3 py-1 bg-gray-800 rounded disabled:opacity-40"
                        >
                            Next
                        </button>
                    </div>
                )}

            </div>

            <ViolationDrawer
                open={open}
                data={selected}
                onClose={() => setOpen(false)}
            />
        </div>
    );
};

export default ViolationTable;
