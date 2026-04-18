import { apiFetch } from "./api";
import type { Remediation } from "../types";

export const fetchRemediations = async () => {
    const res = await apiFetch("/remediations");

    if (!res.ok) {
        throw new Error("Failed to fetch remediations");
    }

    return (await res.json()) as Remediation[];
};

export const approveRemediation = async (id: number) => {
    const res = await apiFetch(`/remediations/${id}/approve`, {
        method: "POST",
    });

    if (!res.ok) {
        throw new Error("Failed to approve remediation");
    }

    return res.json();
};

export const retryRemediation = async (id: number) => {
    const res = await apiFetch(`/remediations/${id}/retry`, {
        method: "POST",
    });

    if (!res.ok) {
        throw new Error("Failed to retry remediation");
    }

    return (await res.json()) as Remediation;
};

export const reverifyRemediation = async (id: number) => {
    const res = await apiFetch(`/remediations/${id}/reverify`, {
        method: "POST",
    });

    if (!res.ok) {
        throw new Error("Failed to reverify remediation");
    }

    return (await res.json()) as Remediation;
};
