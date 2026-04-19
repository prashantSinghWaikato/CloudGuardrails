import { useEffect, useState } from "react";
import {
    fetchAccounts,
    createAccount,
    updateAccount,
    activateAccount,
    scanAccount,
    fetchAccountScanHistory,
    deleteAccount,
} from "../api/accountApi";
import AccountModal from "../components/AccountModal";
import AccountActivationModal from "../components/AccountActivationModal";
import type { AccountActivationFormData, AccountFormData, AccountScanRun, CloudAccount } from "../types";

const Accounts = () => {
    const [accounts, setAccounts] = useState<CloudAccount[]>([]);
    const [loading, setLoading] = useState(true);

    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<CloudAccount | null>(null);
    const [activationTarget, setActivationTarget] = useState<CloudAccount | null>(null);
    const [scanTargetId, setScanTargetId] = useState<number | null>(null);
    const [historyTarget, setHistoryTarget] = useState<CloudAccount | null>(null);
    const [historyRuns, setHistoryRuns] = useState<AccountScanRun[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);

    const load = async () => {
        try {
            setLoading(true);
            const data = await fetchAccounts();
            setAccounts(data);
            window.dispatchEvent(new Event("accountsUpdated"));
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const handleSave = async (form: AccountFormData) => {
        if (editing) {
            const updated = await updateAccount(editing.id, form);
            setActivationTarget(updated);
        } else {
            const created = await createAccount(form);
            setActivationTarget(created);
        }

        setOpen(false);
        setEditing(null);
        await load();
    };

    const handleActivate = async (accountId: number, data: AccountActivationFormData) => {
        await activateAccount(accountId, data);
        await load();
    };

    const handleScan = async (accountId: number) => {
        try {
            setScanTargetId(accountId);
            await scanAccount(accountId);
            await load();
            if (historyTarget?.id === accountId) {
                await loadHistory(accountId);
            }
        } catch (e) {
            console.error(e);
        } finally {
            setScanTargetId(null);
        }
    };

    const loadHistory = async (accountId: number) => {
        try {
            setHistoryLoading(true);
            const data = await fetchAccountScanHistory(accountId);
            setHistoryRuns(data);
        } catch (e) {
            console.error(e);
        } finally {
            setHistoryLoading(false);
        }
    };

    const openHistory = async (account: CloudAccount) => {
        setHistoryTarget(account);
        await loadHistory(account.id);
    };

    const renderStatus = (account: CloudAccount) => {
        const active = account.monitoringEnabled && account.activationStatus === "ACTIVE";
        const label = active ? "Active" : account.activationStatus ?? "Pending";
        const classes = active
            ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-300"
            : "border-amber-500/25 bg-amber-500/10 text-amber-300";

        return (
            <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${classes}`}>
                {label}
            </span>
        );
    };

    const renderLastSync = (account: CloudAccount) => {
        return (
            <div className="space-y-1 text-xs">
                <div className="text-gray-200">
                    {account.lastSyncAt ? new Date(account.lastSyncAt).toLocaleString() : "Never"}
                </div>
                <div className="text-gray-400">
                    {account.lastSyncStatus ?? "No status"}
                </div>
            </div>
        );
    };

    const renderHistoryStatus = (run: AccountScanRun) => {
        const success = run.status === "SUCCESS";
        return (
            <span
                className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${
                    success
                        ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-300"
                        : "border-amber-500/25 bg-amber-500/10 text-amber-300"
                }`}
            >
                {run.status ?? "UNKNOWN"}
            </span>
        );
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this account?")) return;

        try {
            await deleteAccount(id);
            await load();
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <div className="p-6 space-y-6">
            <div className="flex items-center justify-between gap-4">
                <h1 className="text-xl font-semibold">Cloud Accounts</h1>

                <button
                    onClick={() => {
                        setEditing(null);
                        setOpen(true);
                    }}
                    className="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-[0_10px_30px_-12px_rgba(37,99,235,0.9)] transition hover:-translate-y-0.5 hover:bg-blue-500 hover:shadow-[0_16px_40px_-16px_rgba(59,130,246,0.95)] focus:outline-none focus:ring-2 focus:ring-blue-400/70 focus:ring-offset-2 focus:ring-offset-gray-950 active:translate-y-0 active:bg-blue-700"
                >
                    + Add Account
                </button>
            </div>

            {/* Table */}
            <div className="bg-white/5 border border-white/10 rounded-xl overflow-hidden">

                {loading ? (
                    <p className="p-4">Loading...</p>
                ) : accounts.length === 0 ? (
                    <p className="p-4 text-gray-400">No accounts</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead className="bg-white/3">
                                <tr className="border-b border-gray-800 text-gray-400">
                                    <th className="px-4 py-3 text-left font-medium">Name</th>
                                    <th className="px-4 py-3 text-left font-medium">Account ID</th>
                                    <th className="px-4 py-3 text-left font-medium">Provider</th>
                                    <th className="px-4 py-3 text-left font-medium">Region</th>
                                    <th className="px-4 py-3 text-left font-medium">Monitoring</th>
                                    <th className="px-4 py-3 text-left font-medium">Last Sync</th>
                                    <th className="px-4 py-3 text-right font-medium">Actions</th>
                                </tr>
                            </thead>

                            <tbody>
                                {accounts.map((a) => (
                                    <tr
                                        key={a.id}
                                        className="border-b border-gray-800/80 last:border-0"
                                    >
                                        <td className="px-4 py-4">
                                            <div className="font-medium text-gray-100">
                                                {a.name}
                                            </div>
                                        </td>
                                        <td className="px-4 py-4 font-medium text-gray-100">{a.accountId}</td>
                                        <td className="px-4 py-4 text-gray-300">{a.provider}</td>
                                        <td className="px-4 py-4 text-gray-300">{a.region}</td>
                                        <td className="px-4 py-4">
                                            {renderStatus(a)}
                                        </td>
                                        <td className="px-4 py-4">{renderLastSync(a)}</td>

                                        <td className="px-4 py-4">
                                            <div className="flex items-center justify-end gap-3">
                                                <button
                                                    onClick={() => handleScan(a.id)}
                                                    disabled={!a.monitoringEnabled || scanTargetId === a.id}
                                                    className="text-cyan-400 hover:text-cyan-300 disabled:cursor-not-allowed disabled:opacity-40"
                                                >
                                                    {scanTargetId === a.id ? "Scanning..." : "Scan Now"}
                                                </button>

                                                <button
                                                    onClick={() => setActivationTarget(a)}
                                                    className="text-emerald-400 hover:text-emerald-300"
                                                >
                                                    {a.monitoringEnabled ? "Re-activate" : "Activate"}
                                                </button>

                                                <button
                                                    onClick={() => openHistory(a)}
                                                    className="text-violet-400 hover:text-violet-300"
                                                >
                                                    History
                                                </button>

                                                <button
                                                    onClick={() => {
                                                        setEditing(a);
                                                        setOpen(true);
                                                    }}
                                                    className="text-blue-400 hover:text-blue-300"
                                                >
                                                    Edit
                                                </button>

                                                <button
                                                    onClick={() => handleDelete(a.id)}
                                                    className="text-red-400 hover:text-red-300"
                                                >
                                                    Delete
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {historyTarget && (
                <div className="rounded-xl border border-white/10 bg-white/5 p-5">
                    <div className="mb-4 flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-lg font-semibold text-gray-100">
                                Scan History: {historyTarget.name}
                            </h2>
                            <p className="text-sm text-gray-400">
                                Latest diagnostics and current-state posture scan results for account {historyTarget.accountId}
                            </p>
                        </div>

                        <button
                            onClick={() => {
                                setHistoryTarget(null);
                                setHistoryRuns([]);
                            }}
                            className="text-sm text-gray-400 hover:text-gray-200"
                        >
                            Close
                        </button>
                    </div>

                    {historyLoading ? (
                        <p className="text-sm text-gray-400">Loading scan history...</p>
                    ) : historyRuns.length === 0 ? (
                        <p className="text-sm text-gray-400">No scan history available yet.</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead className="border-b border-gray-800 text-gray-400">
                                    <tr>
                                        <th className="px-3 py-3 text-left font-medium">Started</th>
                                        <th className="px-3 py-3 text-left font-medium">Completed</th>
                                        <th className="px-3 py-3 text-left font-medium">Status</th>
                                        <th className="px-3 py-3 text-left font-medium">Diagnostics</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {historyRuns.map((run) => (
                                        <tr key={run.id} className="border-b border-gray-800/80 last:border-0 align-top">
                                            <td className="px-3 py-3 text-gray-300">
                                                {run.startedAt ? new Date(run.startedAt).toLocaleString() : "-"}
                                            </td>
                                            <td className="px-3 py-3 text-gray-300">
                                                {run.completedAt ? new Date(run.completedAt).toLocaleString() : "-"}
                                            </td>
                                            <td className="px-3 py-3">{renderHistoryStatus(run)}</td>
                                            <td className="px-3 py-3 text-xs text-gray-400">
                                                <div>
                                                    Seen: {run.eventsSeen ?? 0} · Ingested: {run.eventsIngested ?? 0} · Duplicates: {run.duplicatesSkipped ?? 0}
                                                </div>
                                                <div>
                                                    Event violations: {run.violationsCreated ?? 0} · Posture findings: {run.postureFindingsCreated ?? 0}
                                                </div>
                                                <div className="mt-1 text-gray-500">{run.message ?? "-"}</div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}

            {/* Modal */}
            <AccountModal
                key={`${open}-${editing?.id ?? "new"}`}
                open={open}
                onClose={() => setOpen(false)}
                onSave={handleSave}
                initial={editing}
            />

            <AccountActivationModal
                open={activationTarget !== null}
                account={activationTarget}
                onClose={() => setActivationTarget(null)}
                onActivate={handleActivate}
            />
        </div>
    );
};

export default Accounts;
