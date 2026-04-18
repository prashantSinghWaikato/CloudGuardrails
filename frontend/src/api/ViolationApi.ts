import { apiFetch } from "./api";
import type { PageResponse, Violation, ViolationDetail } from "../types";

export const fetchFilteredViolations = async (
    page = 0,
    size = 10,
    severity?: string,
    status?: string
) => {
    let url = `/violations/filter?page=${page}&size=${size}`;

    if (severity) url += `&severity=${encodeURIComponent(severity)}`;
    if (status) url += `&status=${encodeURIComponent(status)}`;

    const res = await apiFetch(url);

    if (!res.ok) {
        throw new Error("Failed to fetch filtered violations");
    }

    return (await res.json()) as PageResponse<Violation>;
};

export const fetchViolationCount = async (status: string) => {
    const res = await apiFetch(
        `/violations/count?status=${encodeURIComponent(status)}`
    );

    if (!res.ok) {
        throw new Error("Failed to fetch violation count");
    }

    return (await res.json()) as number;
};

export const updateViolationStatus = async (id: number, status: string) => {
    const res = await apiFetch(
        `/violations/${id}/status?status=${encodeURIComponent(status)}`,
        {
            method: "PUT",
        }
    );

    if (!res.ok) {
        throw new Error("Failed to update status");
    }

    return res.json();
};

export const fetchViolations = async (page = 0, size = 10) => {
    const res = await apiFetch(`/violations?page=${page}&size=${size}`);

    if (!res.ok) {
        throw new Error("Failed to fetch violations");
    }

    return (await res.json()) as PageResponse<Violation>;
};

export const fetchViolationDetail = async (id: number) => {
    const res = await apiFetch(`/violations/${id}`);

    if (!res.ok) {
        throw new Error("Failed to fetch violation details");
    }

    return (await res.json()) as ViolationDetail;
};

export const fetchRecentViolations = async () => {
    const res = await apiFetch("/violations/recent");

    if (!res.ok) {
        throw new Error("Failed to fetch recent violations");
    }

    return (await res.json()) as Violation[];
};

export const searchViolations = async (
    query: string,
    page = 0,
    size = 10
) => {
    const res = await apiFetch(
        `/violations/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`
    );

    if (!res.ok) {
        throw new Error("Search failed");
    }

    return (await res.json()) as PageResponse<Violation>;
};
