import { apiFetch, readApiError } from "./api";
import type { Rule, RuleUpdateRequest } from "../types";

export const fetchRules = async () => {
    const res = await apiFetch("/rules");

    if (!res.ok) {
        throw new Error(await readApiError(res, "Failed to fetch rules"));
    }

    return (await res.json()) as Rule[];
};

export const updateRule = async (id: number, data: RuleUpdateRequest) => {
    const res = await apiFetch(`/rules/${id}`, {
        method: "PUT",
        body: JSON.stringify(data),
    });

    if (!res.ok) {
        throw new Error(await readApiError(res, "Failed to update rule"));
    }

    return (await res.json()) as Rule;
};

export const setRuleEnabled = async (id: number, enabled: boolean) => {
    const res = await apiFetch(`/rules/${id}/enabled?enabled=${enabled}`, {
        method: "PATCH",
    });

    if (!res.ok) {
        throw new Error(await readApiError(res, "Failed to update rule status"));
    }

    return (await res.json()) as Rule;
};
