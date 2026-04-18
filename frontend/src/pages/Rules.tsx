import { useEffect, useState } from "react";
import { fetchRules, setRuleEnabled, updateRule } from "../api/ruleApi";
import type { Rule, RuleUpdateRequest, Severity } from "../types";

const SEVERITIES: Severity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const REMEDIATION_ACTIONS = [
    "REVOKE_SECURITY_GROUP_RULE",
    "BLOCK_PUBLIC_S3_ACCESS",
    "DISABLE_ACCESS_KEY",
    "TAG_RESOURCE",
    "DEFAULT_ACTION",
];

const emptyEditor = (rule: Rule | null): RuleUpdateRequest => ({
    description: rule?.description ?? "",
    severity: rule?.severity ?? "LOW",
    enabled: rule?.enabled ?? true,
    autoRemediation: rule?.autoRemediation ?? false,
    remediationAction: rule?.remediationAction ?? "DEFAULT_ACTION",
});

const getRemediationActions = (currentAction: string) =>
    REMEDIATION_ACTIONS.includes(currentAction)
        ? REMEDIATION_ACTIONS
        : [currentAction, ...REMEDIATION_ACTIONS];

const RulesPage = () => {
    const [rules, setRules] = useState<Rule[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [editingRule, setEditingRule] = useState<Rule | null>(null);
    const [editor, setEditor] = useState<RuleUpdateRequest>(emptyEditor(null));
    const [saving, setSaving] = useState(false);
    const remediationActions = getRemediationActions(editor.remediationAction);

    const loadRules = async () => {
        setLoading(true);
        setError("");

        try {
            setRules(await fetchRules());
        } catch (loadError) {
            setError(
                loadError instanceof Error ? loadError.message : "Failed to load rules"
            );
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        void loadRules();
    }, []);

    const openEditor = (rule: Rule) => {
        setEditingRule(rule);
        setEditor(emptyEditor(rule));
        setError("");
    };

    const closeEditor = () => {
        setEditingRule(null);
        setEditor(emptyEditor(null));
        setError("");
    };

    const handleToggle = async (rule: Rule) => {
        try {
            const updated = await setRuleEnabled(rule.id, !rule.enabled);
            setRules((current) =>
                current.map((item) => (item.id === updated.id ? updated : item))
            );
        } catch (toggleError) {
            setError(
                toggleError instanceof Error
                    ? toggleError.message
                    : "Failed to toggle rule"
            );
        }
    };

    const handleSave = async () => {
        if (!editingRule) {
            return;
        }

        setSaving(true);
        setError("");

        try {
            const updated = await updateRule(editingRule.id, editor);
            setRules((current) =>
                current.map((item) => (item.id === updated.id ? updated : item))
            );
            closeEditor();
        } catch (saveError) {
            setError(
                saveError instanceof Error ? saveError.message : "Failed to save rule"
            );
        } finally {
            setSaving(false);
        }
    };

    const statusClass = (enabled: boolean) =>
        enabled
            ? "bg-emerald-500/10 text-emerald-300 border border-emerald-500/20"
            : "bg-slate-500/10 text-slate-300 border border-slate-500/20";

    const severityClass = (severity: Severity) => {
        switch (severity) {
            case "CRITICAL":
                return "bg-red-500/10 text-red-300 border border-red-500/20";
            case "HIGH":
                return "bg-orange-500/10 text-orange-300 border border-orange-500/20";
            case "MEDIUM":
                return "bg-yellow-500/10 text-yellow-300 border border-yellow-500/20";
            default:
                return "bg-emerald-500/10 text-emerald-300 border border-emerald-500/20";
        }
    };

    return (
        <div className="flex h-full min-h-0 flex-col gap-6 p-6 overflow-hidden">
            <div className="flex items-center justify-between gap-4">
                <div>
                    <h1 className="text-xl font-semibold text-gray-100">Rules</h1>
                    <p className="mt-1 text-sm text-gray-400">
                        Review rule severity, enablement, and remediation behavior.
                    </p>
                </div>
            </div>

            {error && (
                <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                    {error}
                </div>
            )}

            <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-white/10 bg-white/5 shadow-xl">
                {loading ? (
                    <div className="p-6 text-gray-400">Loading rules...</div>
                ) : (
                    <div className="min-h-0 flex-1 overflow-auto">
                        <table className="w-full min-w-[980px] text-sm">
                            <thead className="sticky top-0 z-10 bg-[#1d2332] text-gray-400">
                                <tr className="border-b border-gray-800">
                                    <th className="px-4 py-3 text-left font-medium">Rule</th>
                                    <th className="px-4 py-3 text-left font-medium">Severity</th>
                                    <th className="px-4 py-3 text-left font-medium">Enabled</th>
                                    <th className="px-4 py-3 text-left font-medium">Auto Remediation</th>
                                    <th className="px-4 py-3 text-left font-medium">Action</th>
                                    <th className="px-4 py-3 text-left font-medium">Description</th>
                                    <th className="px-4 py-3 text-right font-medium">Controls</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rules.map((rule) => (
                                    <tr
                                        key={rule.id}
                                        className="border-b border-gray-800/80 align-top last:border-0"
                                    >
                                        <td className="px-4 py-4 font-medium text-gray-100">
                                            {rule.ruleName}
                                        </td>
                                        <td className="px-4 py-4">
                                            <span
                                                className={`inline-flex rounded-full px-2.5 py-1 text-xs ${severityClass(rule.severity)}`}
                                            >
                                                {rule.severity}
                                            </span>
                                        </td>
                                        <td className="px-4 py-4">
                                            <span
                                                className={`inline-flex rounded-full px-2.5 py-1 text-xs ${statusClass(rule.enabled)}`}
                                            >
                                                {rule.enabled ? "Enabled" : "Disabled"}
                                            </span>
                                        </td>
                                        <td className="px-4 py-4 text-gray-300">
                                            {rule.autoRemediation ? "Enabled" : "Manual"}
                                        </td>
                                        <td className="px-4 py-4 font-mono text-xs text-gray-300">
                                            {rule.remediationAction}
                                        </td>
                                        <td className="px-4 py-4 text-gray-300">
                                            {rule.description}
                                        </td>
                                        <td className="px-4 py-4">
                                            <div className="flex justify-end gap-3">
                                                <button
                                                    onClick={() => void handleToggle(rule)}
                                                    className="text-sm text-gray-300 transition hover:text-white"
                                                >
                                                    {rule.enabled ? "Disable" : "Enable"}
                                                </button>
                                                <button
                                                    onClick={() => openEditor(rule)}
                                                    className="text-sm text-blue-400 transition hover:text-blue-300"
                                                >
                                                    Edit
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

            {editingRule && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
                    <div className="w-full max-w-2xl rounded-2xl border border-white/10 bg-gray-900 p-6 shadow-2xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <h2 className="text-lg font-semibold text-white">
                                    Edit Rule
                                </h2>
                                <p className="mt-1 text-sm text-gray-400">
                                    {editingRule.ruleName}
                                </p>
                            </div>
                            <button
                                onClick={closeEditor}
                                className="text-sm text-gray-400 transition hover:text-white"
                            >
                                Close
                            </button>
                        </div>

                        <div className="mt-6 grid gap-4 sm:grid-cols-2">
                            <label className="space-y-2 text-sm">
                                <span className="text-gray-300">Severity</span>
                                <select
                                    value={editor.severity}
                                    onChange={(e) =>
                                        setEditor((current) => ({
                                            ...current,
                                            severity: e.target.value as Severity,
                                        }))
                                    }
                                    className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                                >
                                    {SEVERITIES.map((severity) => (
                                        <option key={severity} value={severity}>
                                            {severity}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label className="space-y-2 text-sm">
                                <span className="text-gray-300">Remediation Action</span>
                                <select
                                    value={editor.remediationAction}
                                    onChange={(e) =>
                                        setEditor((current) => ({
                                            ...current,
                                            remediationAction: e.target.value,
                                        }))
                                    }
                                    className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                                >
                                    {remediationActions.map((action) => (
                                        <option key={action} value={action}>
                                            {action}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        </div>

                        <div className="mt-4 grid gap-4 sm:grid-cols-2">
                            <label className="flex items-center gap-3 rounded-lg border border-white/10 bg-gray-800 px-3 py-3 text-sm text-gray-200">
                                <input
                                    type="checkbox"
                                    checked={editor.enabled}
                                    onChange={(e) =>
                                        setEditor((current) => ({
                                            ...current,
                                            enabled: e.target.checked,
                                        }))
                                    }
                                />
                                Enabled
                            </label>
                            <label className="flex items-center gap-3 rounded-lg border border-white/10 bg-gray-800 px-3 py-3 text-sm text-gray-200">
                                <input
                                    type="checkbox"
                                    checked={editor.autoRemediation}
                                    onChange={(e) =>
                                        setEditor((current) => ({
                                            ...current,
                                            autoRemediation: e.target.checked,
                                        }))
                                    }
                                />
                                Auto Remediation
                            </label>
                        </div>

                        <label className="mt-4 block space-y-2 text-sm">
                            <span className="text-gray-300">Description</span>
                            <textarea
                                value={editor.description}
                                onChange={(e) =>
                                    setEditor((current) => ({
                                        ...current,
                                        description: e.target.value,
                                    }))
                                }
                                rows={5}
                                className="w-full rounded-lg border border-white/10 bg-gray-800 px-3 py-2.5 text-white outline-none transition focus:border-blue-400/70 focus:ring-2 focus:ring-blue-400/20"
                            />
                        </label>

                        <div className="mt-6 flex justify-end gap-3">
                            <button
                                onClick={closeEditor}
                                className="rounded-lg border border-white/10 px-4 py-2 text-sm text-gray-300 transition hover:bg-white/5"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => void handleSave()}
                                disabled={saving}
                                className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {saving ? "Saving..." : "Save Changes"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default RulesPage;
