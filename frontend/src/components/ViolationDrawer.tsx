import { X } from "lucide-react";
import { useEffect, useState } from "react";
import { fetchViolationDetail } from "../api/ViolationApi";
import type { Violation, ViolationDetail } from "../types";

type Props = {
    open: boolean;
    onClose: () => void;
    data: Violation | null;
};

const displayStatus = (status: string | null | undefined) => {
    if (!status) {
        return "N/A";
    }

    return status === "FIXED" ? "CLOSED" : status;
};

const ViolationDrawer = ({ open, onClose, data }: Props) => {
    const [detail, setDetail] = useState<ViolationDetail | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open || !data) {
            setDetail(null);
            setError("");
            setLoading(false);
            return;
        }

        const load = async () => {
            setLoading(true);
            setError("");

            try {
                setDetail(await fetchViolationDetail(data.id));
            } catch (loadError) {
                setError(
                    loadError instanceof Error
                        ? loadError.message
                        : "Failed to load violation details"
                );
            } finally {
                setLoading(false);
            }
        };

        void load();
    }, [open, data]);

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

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50 flex">

            {/* Overlay */}
            <div
                className="absolute inset-0 bg-black/50"
                onClick={onClose}
            />

            {/* Drawer */}
            <div className="ml-auto w-[400px] h-full bg-gray-900 border-l border-gray-800 p-6 relative z-50 overflow-auto">

                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <h2 className="text-lg font-semibold">Violation Details</h2>
                    <button onClick={onClose}>
                        <X />
                    </button>
                </div>

                {/* Content */}
                {loading && (
                    <div className="py-10 text-sm text-gray-400">Loading details...</div>
                )}

                {error && (
                    <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                        {error}
                    </div>
                )}

                {detail && (
                    <div className="space-y-4 text-sm">
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <p className="text-gray-400">Rule</p>
                                <p className="font-medium">{detail.ruleName}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Severity</p>
                                <p>{detail.severity}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Resource</p>
                                <p>{detail.resourceId}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Status</p>
                                <p>{displayStatus(detail.status)}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Account</p>
                                <p>{detail.accountId}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Provider / Region</p>
                                <p>{detail.provider ?? "N/A"} {detail.region ? `· ${detail.region}` : ""}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Event Type</p>
                                <p>{detail.eventType ?? "N/A"}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">External Event ID</p>
                                <p className="break-all">{detail.externalEventId ?? "N/A"}</p>
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <p className="text-gray-400">Created</p>
                                <p>{formatDateTime(detail.createdAt)}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Updated</p>
                                <p>{formatDateTime(detail.updatedAt)}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Event Timestamp</p>
                                <p>{formatDateTime(detail.eventTimestamp)}</p>
                            </div>
                            <div>
                                <p className="text-gray-400">Organization</p>
                                <p>{detail.organizationName ?? "N/A"}</p>
                            </div>
                        </div>

                        {detail.remediation && (
                            <div className="rounded-xl border border-white/10 bg-black/20 p-4">
                                <p className="mb-3 text-gray-400">Linked Remediation</p>
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <p className="text-xs text-gray-500">Action</p>
                                        <p>{detail.remediation.action}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Status</p>
                                        <p>{detail.remediation.status}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Attempts</p>
                                        <p>{detail.remediation.attemptCount}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Executed At</p>
                                        <p>{formatDateTime(detail.remediation.executedAt)}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Verification</p>
                                        <p>{detail.remediation.verificationStatus ?? "N/A"}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Last Verified</p>
                                        <p>{formatDateTime(detail.remediation.lastVerifiedAt)}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Triggered By</p>
                                        <p>{detail.remediation.lastTriggeredBy ?? "N/A"}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Trigger Source</p>
                                        <p>{detail.remediation.lastTriggerSource ?? "N/A"}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Target Account</p>
                                        <p>{detail.remediation.targetAccountId ?? "N/A"}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Target Resource</p>
                                        <p>{detail.remediation.targetResourceId ?? "N/A"}</p>
                                    </div>
                                </div>
                                {detail.remediation.verificationMessage && (
                                    <div className="mt-4 rounded-lg border border-white/10 bg-black/20 p-3">
                                        <p className="text-xs text-gray-500">Verification Message</p>
                                        <p className="mt-1 text-sm text-gray-200">
                                            {detail.remediation.verificationMessage}
                                        </p>
                                    </div>
                                )}
                            </div>
                        )}

                        <div>
                            <p className="text-gray-400 mb-2">Event Payload</p>
                            <pre className="bg-black/40 p-3 rounded text-xs overflow-auto">
                                {JSON.stringify(detail.payload, null, 2)}
                            </pre>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ViolationDrawer;
