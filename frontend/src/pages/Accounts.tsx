import { useEffect, useState } from "react";
import {
    fetchAccounts,
    createAccount,
    updateAccount,
    deleteAccount,
} from "../api/accountApi";
import AccountModal from "../components/AccountModal";
import type { AccountFormData, CloudAccount } from "../types";

const Accounts = () => {
    const [accounts, setAccounts] = useState<CloudAccount[]>([]);
    const [loading, setLoading] = useState(true);

    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<CloudAccount | null>(null);

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
            await updateAccount(editing.id, form);
        } else {
            await createAccount(form);
        }

        setOpen(false);
        setEditing(null);
        await load();
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
                                            <div className="flex items-center justify-end gap-3">
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

            {/* Modal */}
            <AccountModal
                key={`${open}-${editing?.id ?? "new"}`}
                open={open}
                onClose={() => setOpen(false)}
                onSave={handleSave}
                initial={editing}
            />
        </div>
    );
};

export default Accounts;
