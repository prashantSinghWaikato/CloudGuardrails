import { useState } from "react";
import { validateCloudAccount } from "../api/accountApi";
import type {
    AccountFormData,
    AccountValidationResponse,
    CloudAccount,
} from "../types";

type Props = {
    open: boolean;
    onClose: () => void;
    onSave: (data: AccountFormData) => Promise<void>;
    initial: CloudAccount | null;
};

const AccountModal = ({ open, onClose, onSave, initial }: Props) => {
    const [form, setForm] = useState<AccountFormData>({
        name: initial?.name ?? "",
        accountId: initial?.accountId ?? "",
        provider: initial?.provider ?? "AWS",
        region: initial?.region ?? "",
        accessKey: "",
        secretKey: "",
    });
    const [error, setError] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [isValidating, setIsValidating] = useState(false);
    const [validationResult, setValidationResult] =
        useState<AccountValidationResponse | null>(null);

    const updateField = <K extends keyof AccountFormData>(
        key: K,
        value: AccountFormData[K]
    ) => {
        setForm((current) => ({ ...current, [key]: value }));
        setError("");
        setValidationResult(null);
    };

    const validateRequiredFields = () => {
        if (
            !form.name.trim() ||
            !form.accountId.trim() ||
            !form.provider.trim() ||
            !form.region.trim() ||
            !form.accessKey.trim() ||
            !form.secretKey.trim()
        ) {
            setError("Account name, account ID, provider, region, access key, and secret key are required.");
            return false;
        }

        return true;
    };

    const handleValidate = async () => {
        if (!validateRequiredFields()) {
            return;
        }

        setIsValidating(true);
        setError("");

        try {
            const result = await validateCloudAccount(form);
            setValidationResult(result);
        } catch (validationError) {
            setValidationResult(null);
            setError(
                validationError instanceof Error
                    ? validationError.message
                    : "Account validation failed"
            );
        } finally {
            setIsValidating(false);
        }
    };

    const handleSave = async () => {
        if (!validateRequiredFields()) {
            return;
        }

        setIsSaving(true);
        setError("");

        try {
            await onSave(form);
        } catch (saveError) {
            setError(
                saveError instanceof Error ? saveError.message : "Failed to save account"
            );
        } finally {
            setIsSaving(false);
        }
    };

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
            <div className="w-full max-w-lg space-y-5 rounded-2xl border border-white/10 bg-gray-900 p-6 shadow-2xl">
                <div className="space-y-1">
                    <h2 className="text-lg font-semibold">
                        {initial ? "Edit Account" : "Add Account"}
                    </h2>
                    <p className="text-sm text-gray-400">
                        Validate the AWS credentials before saving to confirm the account ID,
                        IAM principal, and region are correct.
                    </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Account Name</span>
                        <input
                            placeholder="Production Security"
                            value={form.name}
                            onChange={(e) => updateField("name", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        />
                    </label>

                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Account ID</span>
                        <input
                            placeholder="824756206785"
                            value={form.accountId}
                            onChange={(e) => updateField("accountId", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        />
                    </label>

                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Provider</span>
                        <select
                            value={form.provider}
                            onChange={(e) => updateField("provider", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        >
                            <option value="AWS">AWS</option>
                        </select>
                    </label>

                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Region</span>
                        <input
                            placeholder="ap-southeast-2"
                            value={form.region}
                            onChange={(e) => updateField("region", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        />
                    </label>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Access Key</span>
                        <input
                            placeholder="AKIA..."
                            value={form.accessKey}
                            onChange={(e) => updateField("accessKey", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        />
                    </label>

                    <label className="space-y-2 text-sm">
                        <span className="text-gray-300">Secret Key</span>
                        <input
                            type="password"
                            placeholder={initial ? "Re-enter to update" : "AWS secret key"}
                            value={form.secretKey}
                            onChange={(e) => updateField("secretKey", e.target.value)}
                            className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                        />
                    </label>
                </div>

                {validationResult && (
                    <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm">
                        <p className="font-medium text-emerald-300">
                            {validationResult.message}
                        </p>
                        <div className="mt-3 grid gap-2 text-gray-200">
                            <p>
                                <span className="text-gray-400">Account:</span>{" "}
                                {validationResult.accountId}
                            </p>
                            <p>
                                <span className="text-gray-400">ARN:</span>{" "}
                                {validationResult.arn}
                            </p>
                            <p>
                                <span className="text-gray-400">User ID:</span>{" "}
                                {validationResult.userId}
                            </p>
                        </div>
                    </div>
                )}

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
                        Cancel
                    </button>

                    <button
                        onClick={handleValidate}
                        disabled={isValidating || isSaving}
                        className="rounded-lg border border-blue-400/30 bg-blue-500/10 px-4 py-2 text-sm font-medium text-blue-300 transition hover:bg-blue-500/20 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {isValidating ? "Validating..." : "Validate Account"}
                    </button>

                    <button
                        onClick={handleSave}
                        disabled={isSaving || isValidating}
                        className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {isSaving ? "Saving..." : initial ? "Update Account" : "Save Account"}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default AccountModal;
