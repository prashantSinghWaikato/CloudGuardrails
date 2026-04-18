import { Fragment, useEffect, useState } from "react";
import {
    approveRemediation,
    fetchRemediations,
    retryRemediation,
    reverifyRemediation,
} from "../api/RemediationApi";
import { connectWebSocket } from "../api/Websocket";
import type { Remediation, RemediationExecution } from "../types";

const RemediationTable = () => {
    const [data, setData] = useState<Remediation[]>([]);
    const [loading, setLoading] = useState(true);
    const [expandedId, setExpandedId] = useState<number | null>(null);

    const load = async () => {
        setLoading(true);
        try {
            const res = await fetchRemediations();
            setData(res || []);
        } catch (e) {
            console.error("Failed to load remediations", e);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        void load();

        const disconnect = connectWebSocket(
            () => {},
            (newRemediation) => {
                setData((prev) => {
                    const exists = prev.find((item) => item.id === newRemediation.id);

                    if (exists) {
                        return prev.map((item) =>
                            item.id === newRemediation.id ? newRemediation : item
                        );
                    }

                    return [newRemediation, ...prev];
                });
            }
        );

        return () => {
            disconnect();
        };
    }, []);

    const handleApprove = async (id: number) => {
        try {
            await approveRemediation(id);
            await load();
        } catch (e) {
            console.error("Approve failed", e);
        }
    };

    const handleRetry = async (id: number) => {
        try {
            await retryRemediation(id);
            await load();
        } catch (e) {
            console.error("Retry failed", e);
        }
    };

    const handleReverify = async (id: number) => {
        try {
            await reverifyRemediation(id);
            await load();
        } catch (e) {
            console.error("Reverify failed", e);
        }
    };

    const getStatusClass = (status: string) => {
        switch (status) {
            case "EXECUTED":
                return "bg-emerald-500/10 text-emerald-300 border border-emerald-500/20";
            case "VERIFIED":
                return "bg-emerald-500/15 text-emerald-200 border border-emerald-400/30";
            case "VERIFICATION_FAILED":
                return "bg-rose-500/10 text-rose-300 border border-rose-500/20";
            case "MANUAL_ACTION_REQUIRED":
                return "bg-amber-500/10 text-amber-300 border border-amber-500/20";
            case "FAILED":
                return "bg-red-500/10 text-red-300 border border-red-500/20";
            case "EXECUTING":
                return "bg-blue-500/10 text-blue-300 border border-blue-500/20";
            case "APPROVED":
                return "bg-cyan-500/10 text-cyan-300 border border-cyan-500/20";
            case "PENDING":
                return "bg-slate-500/10 text-slate-300 border border-slate-500/20";
            default:
                return "bg-slate-500/10 text-slate-300 border border-slate-500/20";
        }
    };

    const getVerificationTone = (status?: string | null) => {
        switch (status) {
            case "PASSED":
                return "border-emerald-500/20 bg-emerald-500/10 text-emerald-200";
            case "FAILED":
            case "ERROR":
                return "border-rose-500/20 bg-rose-500/10 text-rose-200";
            default:
                return "border-slate-500/20 bg-slate-500/10 text-slate-200";
        }
    };

    const formatDateTime = (value: string | null) => {
        if (!value) {
            return "N/A";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString();
    };

    const formatResponse = (response: Record<string, unknown> | null | undefined) => {
        if (!response) {
            return null;
        }

        return JSON.stringify(response, null, 2);
    };

    const summarizeExecution = (execution: RemediationExecution) => {
        return (
            execution.verificationMessage ??
            execution.status ??
            "Execution completed without a summary message"
        );
    };

    return (
        <div className="rounded-2xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-md">
            <h2 className="mb-4 text-lg font-semibold text-gray-200">
                All Remediations
            </h2>

            {loading ? (
                <div className="py-10 text-center text-gray-400">Loading...</div>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full min-w-[980px] text-sm">
                        <thead>
                            <tr className="border-b border-gray-800 text-gray-400">
                                <th className="py-2 text-left">Action</th>
                                <th className="text-left">Rule</th>
                                <th className="text-left">Resource</th>
                                <th className="text-left">Verification</th>
                                <th className="text-left">Status</th>
                                <th className="text-right">Controls</th>
                            </tr>
                        </thead>

                        <tbody>
                            {data.length === 0 ? (
                                <tr>
                                    <td colSpan={6} className="py-6 text-center text-gray-400">
                                        No remediations
                                    </td>
                                </tr>
                            ) : (
                                data.map((r) => {
                                    const expanded = expandedId === r.id;
                                    const latestExecution = r.history[0] ?? null;
                                    const verificationState =
                                        r.verificationStatus ??
                                        latestExecution?.verificationStatus ??
                                        "PENDING";
                                    const verificationMessage =
                                        r.verificationMessage ??
                                        latestExecution?.verificationMessage ??
                                        null;
                                    const responseBody = formatResponse(r.response);
                                    const hasDetails =
                                        Boolean(responseBody) ||
                                        Boolean(verificationMessage) ||
                                        r.history.length > 0;

                                    return (
                                        <Fragment key={r.id}>
                                            <tr className="border-b border-gray-800 align-top">
                                                <td className="py-3">{r.action}</td>
                                                <td className="py-3">{r.ruleName}</td>
                                                <td className="py-3">{r.resourceId}</td>
                                                <td className="py-3">
                                                    <span
                                                        className={`inline-flex rounded-full px-2.5 py-1 text-xs ${getVerificationTone(
                                                            verificationState
                                                        )}`}
                                                    >
                                                        {verificationState}
                                                    </span>
                                                </td>
                                                <td className="py-3">
                                                    <span
                                                        className={`inline-flex rounded-full px-2.5 py-1 text-xs ${getStatusClass(
                                                            r.status
                                                        )}`}
                                                    >
                                                        {r.status}
                                                    </span>
                                                </td>
                                                <td className="py-3">
                                                    <div className="flex items-center justify-end gap-2">
                                                        {r.status === "PENDING" && (
                                                            <button
                                                                onClick={() => void handleApprove(r.id)}
                                                                className="rounded bg-blue-600 px-2 py-1 text-xs hover:bg-blue-700"
                                                            >
                                                                Approve
                                                            </button>
                                                        )}

                                                        {(r.status === "FAILED" ||
                                                            r.status === "MANUAL_ACTION_REQUIRED" ||
                                                            r.status === "VERIFICATION_FAILED") && (
                                                            <button
                                                                onClick={() => void handleRetry(r.id)}
                                                                className="rounded bg-amber-500/20 px-2 py-1 text-xs text-amber-200 hover:bg-amber-500/30"
                                                            >
                                                                Retry
                                                            </button>
                                                        )}

                                                        {(r.status === "EXECUTED" ||
                                                            r.status === "VERIFICATION_FAILED" ||
                                                            r.status === "VERIFIED") && (
                                                            <button
                                                                onClick={() => void handleReverify(r.id)}
                                                                className="rounded bg-cyan-500/15 px-2 py-1 text-xs text-cyan-200 hover:bg-cyan-500/25"
                                                            >
                                                                Reverify
                                                            </button>
                                                        )}

                                                        {hasDetails && (
                                                            <button
                                                                onClick={() =>
                                                                    setExpandedId((current) =>
                                                                        current === r.id ? null : r.id
                                                                    )
                                                                }
                                                                className="rounded border border-white/10 px-2 py-1 text-xs text-gray-300 hover:bg-white/5"
                                                            >
                                                                {expanded ? "Hide Details" : "View Details"}
                                                            </button>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>

                                            {expanded && (
                                                <tr className="border-b border-gray-800 bg-black/10">
                                                    <td colSpan={6} className="p-4">
                                                        <div className="space-y-4">
                                                            <div className="grid gap-3 xl:grid-cols-3">
                                                                <div className="rounded-lg border border-white/10 bg-black/20 p-3">
                                                                    <p className="text-xs uppercase tracking-wide text-gray-500">
                                                                        Audit
                                                                    </p>
                                                                    <dl className="mt-3 space-y-2 text-sm text-slate-200">
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Attempts</dt>
                                                                            <dd>{r.attemptCount}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Triggered By</dt>
                                                                            <dd>{r.lastTriggeredBy ?? "N/A"}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Trigger Source</dt>
                                                                            <dd>{r.lastTriggerSource ?? "N/A"}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Target Account</dt>
                                                                            <dd>{r.targetAccountId ?? "N/A"}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Target Resource</dt>
                                                                            <dd>{r.targetResourceId ?? "N/A"}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Executed</dt>
                                                                            <dd>{formatDateTime(r.executedAt)}</dd>
                                                                        </div>
                                                                        <div className="flex items-start justify-between gap-3">
                                                                            <dt className="text-slate-500">Last Verified</dt>
                                                                            <dd>{formatDateTime(r.lastVerifiedAt)}</dd>
                                                                        </div>
                                                                    </dl>
                                                                </div>

                                                                <div
                                                                    className={`rounded-lg border p-3 ${getVerificationTone(
                                                                        verificationState
                                                                    )}`}
                                                                >
                                                                    <p className="text-xs uppercase tracking-wide opacity-80">
                                                                        Verification
                                                                    </p>
                                                                    <p className="mt-2 text-sm font-medium">
                                                                        {verificationState}
                                                                    </p>
                                                                    <p className="mt-2 text-xs opacity-90">
                                                                        {verificationMessage ??
                                                                            "No verification message provided"}
                                                                    </p>
                                                                </div>

                                                                {latestExecution && (
                                                                    <div className="rounded-lg border border-white/10 bg-black/20 p-3">
                                                                        <p className="text-xs uppercase tracking-wide text-gray-500">
                                                                            Latest Execution
                                                                        </p>
                                                                        <p className="mt-2 text-sm font-medium text-white">
                                                                            {summarizeExecution(latestExecution)}
                                                                        </p>
                                                                        <p className="mt-1 text-xs text-slate-400">
                                                                            Attempt {latestExecution.attemptNumber ?? "N/A"} · {latestExecution.triggerSource ?? "N/A"}
                                                                        </p>
                                                                        <p className="mt-1 text-xs text-slate-400">
                                                                            Started {formatDateTime(latestExecution.startedAt)}
                                                                        </p>
                                                                        <p className="mt-1 text-xs text-slate-400">
                                                                            Completed {formatDateTime(latestExecution.completedAt)}
                                                                        </p>
                                                                    </div>
                                                                )}
                                                            </div>

                                                            {r.history.length > 0 && (
                                                                <div>
                                                                    <p className="text-xs uppercase tracking-wide text-gray-500">
                                                                        Execution History
                                                                    </p>
                                                                    <div className="mt-2 overflow-auto rounded-lg border border-white/10">
                                                                        <table className="w-full min-w-[760px] text-left text-xs">
                                                                            <thead className="bg-white/5 text-slate-400">
                                                                                <tr>
                                                                                    <th className="px-3 py-2">Attempt</th>
                                                                                    <th className="px-3 py-2">Trigger</th>
                                                                                    <th className="px-3 py-2">Actor</th>
                                                                                    <th className="px-3 py-2">Status</th>
                                                                                    <th className="px-3 py-2">Verification</th>
                                                                                    <th className="px-3 py-2">Started</th>
                                                                                    <th className="px-3 py-2">Completed</th>
                                                                                </tr>
                                                                            </thead>
                                                                            <tbody>
                                                                                {r.history.map((execution) => (
                                                                                    <tr
                                                                                        key={execution.id}
                                                                                        className="border-t border-white/10 text-slate-200"
                                                                                    >
                                                                                        <td className="px-3 py-2">
                                                                                            {execution.attemptNumber ?? "N/A"}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {execution.triggerSource ?? "N/A"}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {execution.triggeredBy ?? "N/A"}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {execution.status ?? "N/A"}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {execution.verificationStatus ?? "N/A"}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {formatDateTime(execution.startedAt)}
                                                                                        </td>
                                                                                        <td className="px-3 py-2">
                                                                                            {formatDateTime(execution.completedAt)}
                                                                                        </td>
                                                                                    </tr>
                                                                                ))}
                                                                            </tbody>
                                                                        </table>
                                                                    </div>
                                                                </div>
                                                            )}

                                                            {responseBody && (
                                                                <div>
                                                                    <p className="text-xs uppercase tracking-wide text-gray-500">
                                                                        Raw Response
                                                                    </p>
                                                                    <pre className="mt-2 overflow-auto rounded-lg bg-black/30 p-3 text-xs text-gray-200">
                                                                        {responseBody}
                                                                    </pre>
                                                                </div>
                                                            )}
                                                        </div>
                                                    </td>
                                                </tr>
                                            )}
                                        </Fragment>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default RemediationTable;
