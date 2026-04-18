import { apiFetch, readApiError } from "./api";
import type {
    AccountFormData,
    AccountValidationResponse,
    CloudAccount,
} from "../types";

export const fetchAccounts = async () => {
    const res = await apiFetch("/accounts");
    if (!res.ok) throw new Error(await readApiError(res, "Failed to fetch accounts"));
    return (await res.json()) as CloudAccount[];
};

export const createAccount = async (data: AccountFormData) => {
    const res = await apiFetch("/accounts", {
        method: "POST",
        body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(await readApiError(res, "Failed to create account"));
    return (await res.json()) as CloudAccount;
};

export const updateAccount = async (id: number, data: AccountFormData) => {
    const res = await apiFetch(`/accounts/${id}`, {
        method: "PUT",
        body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(await readApiError(res, "Failed to update account"));
    return (await res.json()) as CloudAccount;
};

export const validateCloudAccount = async (data: AccountFormData) => {
    const res = await apiFetch("/accounts/validate", {
        method: "POST",
        body: JSON.stringify(data),
    });

    if (!res.ok) {
        throw new Error(await readApiError(res, "Failed to validate account"));
    }

    return (await res.json()) as AccountValidationResponse;
};

export const deleteAccount = async (id: number) => {
    const res = await apiFetch(`/accounts/${id}`, {
        method: "DELETE",
    });
    if (!res.ok) throw new Error(await readApiError(res, "Failed to delete account"));
};
