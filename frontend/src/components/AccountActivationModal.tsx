import { useEffect, useState } from "react";
import type { AccountActivationFormData, CloudAccount } from "../types";

type Props = {
    open: boolean;
    account: CloudAccount | null;
    onClose: () => void;
    onActivate: (accountId: number, data: AccountActivationFormData) => Promise<void>;
};

const AccountActivationModal = ({ open, account, onClose, onActivate }: Props) => {
    const [form, setForm] = useState<AccountActivationFormData>({
        roleArn: account?.roleArn ?? "",
        externalId: "",
    });
    const [error, setError] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        setForm({
            roleArn: account?.roleArn ?? "",
            externalId: "",
        });
        setError("");
        setIsSaving(false);
    }, [account]);

    if (!open || !account) {
        return null;
    }

    const handleSubmit = async () => {
        if (!form.roleArn.trim()) {
            setError("Role ARN is required to activate AWS monitoring.");
            return;
        }

        setIsSaving(true);
        setError("");

        try {
            await onActivate(account.id, {
                roleArn: form.roleArn.trim(),
                externalId: form.externalId.trim(),
            });
            onClose();
        } catch (activationError) {
            setError(
                activationError instanceof Error
                    ? activationError.message
                    : "Failed to activate account"
            );
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
            <div className="w-full max-w-xl space-y-5 rounded-2xl border border-white/10 bg-gray-900 p-6 shadow-2xl">
                <div className="space-y-1">
                    <h2 className="text-lg font-semibold">Activate AWS Monitoring</h2>
                    <p className="text-sm text-gray-400">
                        Finish onboarding by providing the IAM role Guardrails should assume for
                        scheduled AWS polling and remediation. This replaces the forwarder-based setup.
                    </p>
                </div>

                <div className="rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-gray-300">
                    <p><span className="text-gray-500">Account:</span> {account.name}</p>
                    <p><span className="text-gray-500">AWS Account ID:</span> {account.accountId}</p>
                    <p><span className="text-gray-500">Region:</span> {account.region}</p>
                </div>

                <label className="block space-y-2 text-sm">
                    <span className="text-gray-300">Role ARN</span>
                    <input
                        placeholder="arn:aws:iam::123456789012:role/GuardrailsAccessRole"
                        value={form.roleArn}
                        onChange={(e) => {
                            setForm((current) => ({ ...current, roleArn: e.target.value }));
                            setError("");
                        }}
                        className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                    />
                </label>

                <label className="block space-y-2 text-sm">
                    <span className="text-gray-300">External ID (Optional)</span>
                    <input
                        placeholder="external-id-if-your-role-requires-it"
                        value={form.externalId}
                        onChange={(e) => {
                            setForm((current) => ({ ...current, externalId: e.target.value }));
                            setError("");
                        }}
                        className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                    />
                </label>

                <div className="rounded-xl border border-blue-400/20 bg-blue-500/10 p-4 text-sm text-blue-100">
                    Guardrails will validate the role immediately and only activate monitoring if
                    the assumed role resolves to this AWS account.
                </div>

                {error && (
                    <div className="rounded-xl border border-red-500/25 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                        {error}
                    </div>
                )}

                <div className="flex justify-end gap-3">
                    <button
                        onClick={onClose}
                        className="rounded-lg border border-white/10 px-4 py-2 text-sm text-gray-300 transition hover:bg-white/5"
                    >
                        Later
                    </button>

                    <button
                        onClick={handleSubmit}
                        disabled={isSaving}
                        className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {isSaving ? "Activating..." : "Activate Monitoring"}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default AccountActivationModal;
