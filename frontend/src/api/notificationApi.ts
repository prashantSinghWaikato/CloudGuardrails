import { apiFetch } from "./api";
import type { Notification } from "../types";

export const fetchNotifications = async (unreadOnly = false) => {
    const res = await apiFetch(`/notifications?unreadOnly=${unreadOnly}`);

    if (!res.ok) {
        throw new Error("Failed to fetch notifications");
    }

    return (await res.json()) as Notification[];
};

export const fetchUnreadNotificationCount = async () => {
    const res = await apiFetch("/notifications/unread-count");

    if (!res.ok) {
        throw new Error("Failed to fetch unread notification count");
    }

    const data = (await res.json()) as { count: number };
    return data.count;
};

export const markNotificationRead = async (id: number) => {
    const res = await apiFetch(`/notifications/${id}/read`, {
        method: "PUT",
    });

    if (!res.ok) {
        throw new Error("Failed to mark notification as read");
    }

    return (await res.json()) as Notification;
};

export const markAllNotificationsRead = async () => {
    const res = await apiFetch("/notifications/read-all", {
        method: "PUT",
    });

    if (!res.ok) {
        throw new Error("Failed to mark notifications as read");
    }

    return res.json();
};
