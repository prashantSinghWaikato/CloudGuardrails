import { Bell, LogOut } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { fetchAccounts } from "../api/accountApi";
import { clearAuthState } from "../api/api";
import { fetchUser } from "../api/authService";
import {
    fetchNotifications,
    fetchUnreadNotificationCount,
    markAllNotificationsRead,
    markNotificationRead,
} from "../api/notificationApi";
import { connectWebSocket } from "../api/Websocket";
import type { CloudAccount } from "../types";
import type { Notification } from "../types";
import type { UserResponse as UserType } from "../types/UserResponse";

const PAGE_COPY: Record<string, { title: string; subtitle: string }> = {
    "/dashboard": {
        title: "Security Command Center",
        subtitle: "Realtime overview of cloud posture, violations, and remediation progress.",
    },
    "/violations": {
        title: "Violations",
        subtitle: "Review active findings, filter exposure, and trigger remediation.",
    },
    "/remediations": {
        title: "Remediations",
        subtitle: "Track execution, approval, and verification outcomes across accounts.",
    },
    "/accounts": {
        title: "Accounts",
        subtitle: "Manage onboarded cloud accounts and validation state.",
    },
    "/rules": {
        title: "Rules",
        subtitle: "Tune detection severity and remediation behavior for operational fit.",
    },
};

const Topbar = () => {
    const [open, setOpen] = useState(false);
    const [notificationsOpen, setNotificationsOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const notificationsRef = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const navigate = useNavigate();

    const [user, setUser] = useState<UserType | null>(null);
    const [accounts, setAccounts] = useState<CloudAccount[]>([]);
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);

    useEffect(() => {
        const loadUser = async () => {
            const stored = localStorage.getItem("user");

            try {
                if (stored) {
                    setUser(JSON.parse(stored));
                } else {
                    setUser(null);
                }
            } catch {
                setAccounts([]);
            }

            try {
                const latestUser = (await fetchUser()) as UserType;
                setUser(latestUser);
                localStorage.setItem("user", JSON.stringify(latestUser));
            } catch {
                if (!stored) {
                    setUser(null);
                }
            }

            try {
                const accountData = await fetchAccounts();
                setAccounts(accountData);
            } catch {
                setAccounts([]);
            }
        };

        void loadUser();

        const loadNotifications = async () => {
            try {
                const [items, count] = await Promise.all([
                    fetchNotifications(),
                    fetchUnreadNotificationCount(),
                ]);
                setNotifications(items);
                setUnreadCount(count);
            } catch {
                setNotifications([]);
                setUnreadCount(0);
            }
        };

        void loadNotifications();

        window.addEventListener("userUpdated", loadUser);
        window.addEventListener("accountsUpdated", loadUser);

        const disconnect = connectWebSocket(
            undefined,
            undefined,
            (notification) => {
                setNotifications((current) => {
                    const existing = current.find((item) => item.id === notification.id);
                    setUnreadCount((count) => {
                        if (!existing) {
                            return notification.read ? count : count + 1;
                        }

                        if (existing.read && !notification.read) {
                            return count + 1;
                        }

                        if (!existing.read && notification.read) {
                            return Math.max(0, count - 1);
                        }

                        return count;
                    });

                    if (existing) {
                        return current.map((item) =>
                            item.id === notification.id ? notification : item
                        );
                    }
                    return [notification, ...current].slice(0, 20);
                });
            }
        );

        return () => {
            window.removeEventListener("userUpdated", loadUser);
            window.removeEventListener("accountsUpdated", loadUser);
            disconnect();
        };
    }, []);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
            if (notificationsRef.current && !notificationsRef.current.contains(e.target as Node)) {
                setNotificationsOpen(false);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleLogout = () => {
        clearAuthState();
        window.location.href = "/";
    };

    const handleNotificationClick = async (notification: Notification) => {
        const targetPath = notification.remediationId
            ? "/remediations"
            : notification.violationId
                ? "/violations"
                : null;

        if (!notification.read) {
            try {
                const updated = await markNotificationRead(notification.id);
                setNotifications((current) =>
                    current.map((item) => (item.id === updated.id ? updated : item))
                );
                setUnreadCount((current) => Math.max(0, current - 1));
            } catch {
                // keep current optimistic state untouched on failure
            }
        }

        if (targetPath) {
            setNotificationsOpen(false);
            navigate(targetPath);
        }
    };

    const handleMarkAllRead = async () => {
        try {
            await markAllNotificationsRead();
            setNotifications((current) =>
                current.map((item) => ({ ...item, read: true }))
            );
            setUnreadCount(0);
        } catch {
            // ignore transient failure
        }
    };

    const primaryAccount = accounts[0]?.name ?? accounts[0]?.accountId ?? user?.accounts?.[0] ?? null;
    const pageCopy =
        Object.entries(PAGE_COPY).find(([path]) => location.pathname.startsWith(path))?.[1] ??
        PAGE_COPY["/dashboard"];

    const formatNotificationTime = (value: string) => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString();
    };

    const severityTone = (severity: string) => {
        switch (severity) {
            case "CRITICAL":
                return "border-red-500/30 bg-red-500/10 text-red-200";
            case "HIGH":
                return "border-orange-500/30 bg-orange-500/10 text-orange-200";
            case "MEDIUM":
                return "border-amber-500/30 bg-amber-500/10 text-amber-200";
            case "LOW":
            case "INFO":
                return "border-cyan-500/30 bg-cyan-500/10 text-cyan-200";
            default:
                return "border-slate-500/30 bg-slate-500/10 text-slate-200";
        }
    };

    return (
        <header className="sticky top-0 z-40 border-b border-white/10 bg-[rgba(5,12,22,0.78)] px-6 py-4 backdrop-blur-xl">
            <div className="mx-auto flex max-w-[1380px] items-center justify-between gap-6">
                <div className="min-w-0">
                    <p className="text-[11px] uppercase tracking-[0.28em] text-slate-500">
                        {user?.organization || "Cloud Guardrails"}
                    </p>
                    <h1 className="mt-2 text-2xl font-semibold tracking-tight text-white">
                        {pageCopy.title}
                    </h1>
                    <p className="mt-1 max-w-2xl text-sm text-slate-400">
                        {pageCopy.subtitle}
                    </p>
                </div>

                <div className="relative z-50 flex items-center gap-4">
                    <div className="relative" ref={notificationsRef}>
                        <button
                            onClick={() => setNotificationsOpen((current) => !current)}
                            className="relative rounded-2xl border border-white/10 bg-white/5 p-3 transition hover:bg-white/8"
                        >
                            <Bell size={18} />
                            {unreadCount > 0 && (
                                <span className="absolute -top-1 -right-1 rounded-full bg-rose-500 px-1.5 text-xs text-white">
                                    {unreadCount > 9 ? "9+" : unreadCount}
                                </span>
                            )}
                        </button>

                        {notificationsOpen && (
                            <div className="absolute right-0 top-full z-[9999] mt-3 w-[360px] overflow-hidden rounded-2xl border border-white/10 bg-[rgba(9,16,27,0.97)] shadow-[0_24px_70px_-28px_rgba(2,8,23,0.92)] backdrop-blur-xl">
                                <div className="flex items-center justify-between border-b border-white/10 px-4 py-4">
                                    <div>
                                        <p className="text-sm font-medium text-white">Notifications</p>
                                        <p className="mt-1 text-xs text-slate-400">
                                            Realtime system alerts and remediation outcomes
                                        </p>
                                    </div>
                                    {notifications.length > 0 && (
                                        <button
                                            onClick={handleMarkAllRead}
                                            className="text-xs text-cyan-300 transition hover:text-cyan-200"
                                        >
                                            Mark all read
                                        </button>
                                    )}
                                </div>

                                <div className="max-h-[420px] overflow-auto">
                                    {notifications.length === 0 ? (
                                        <div className="px-4 py-10 text-center text-sm text-slate-400">
                                            No notifications yet
                                        </div>
                                    ) : (
                                        notifications.map((notification) => (
                                            <button
                                                key={notification.id}
                                                onClick={() => void handleNotificationClick(notification)}
                                                className={`w-full border-b border-white/5 px-4 py-4 text-left transition hover:bg-white/5 ${
                                                    notification.read ? "opacity-70" : ""
                                                }`}
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div className="min-w-0">
                                                        <p className="text-sm font-medium text-white">
                                                            {notification.title}
                                                        </p>
                                                        <p className="mt-1 text-sm text-slate-300">
                                                            {notification.message}
                                                        </p>
                                                        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                                                            <span
                                                                className={`rounded-full border px-2 py-0.5 ${severityTone(
                                                                    notification.severity
                                                                )}`}
                                                            >
                                                                {notification.severity}
                                                            </span>
                                                            <span className="text-slate-500">
                                                                {notification.type}
                                                            </span>
                                                            {notification.resourceId && (
                                                                <span className="text-slate-500">
                                                                    {notification.resourceId}
                                                                </span>
                                                            )}
                                                            {(notification.violationId ||
                                                                notification.remediationId) && (
                                                                <span className="text-cyan-300">
                                                                    {notification.remediationId
                                                                        ? `Open remediation #${notification.remediationId}`
                                                                        : `Open violation #${notification.violationId}`}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <p className="mt-2 text-xs text-slate-500">
                                                            {formatNotificationTime(notification.createdAt)}
                                                        </p>
                                                    </div>
                                                    {!notification.read && (
                                                        <span className="mt-1 h-2.5 w-2.5 rounded-full bg-cyan-300" />
                                                    )}
                                                </div>
                                            </button>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="hidden rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-right xl:block">
                        <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                            Primary Account
                        </p>
                        <p className="mt-2 text-sm font-medium text-white">
                            {primaryAccount || "Connect Account"}
                        </p>
                    </div>

                    <div className="relative" ref={dropdownRef}>
                        <button
                            onClick={() => setOpen(!open)}
                            className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-3 py-2 transition hover:bg-white/10"
                        >
                            <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[linear-gradient(135deg,#2f7dff,#58e1ff)] text-sm font-semibold text-slate-950">
                                {user?.name?.charAt(0) || "U"}
                            </div>

                            <div className="text-left">
                                <p className="text-sm font-medium text-white">
                                    {user?.name || "User"}
                                </p>
                                <p className="text-xs text-slate-400">
                                    {user?.organization || "No Organization"}
                                </p>
                            </div>
                        </button>

                        {open && (
                            <div className="absolute right-0 top-full z-[9999] mt-3 w-72 overflow-hidden rounded-2xl border border-white/10 bg-[rgba(9,16,27,0.97)] shadow-[0_24px_70px_-28px_rgba(2,8,23,0.92)] backdrop-blur-xl">
                                <div className="border-b border-white/10 px-4 py-4">
                                    <p className="text-sm font-medium text-white">
                                        {user?.name || "User"}
                                    </p>
                                    <p className="mt-1 text-xs text-slate-400">
                                        {user?.email || ""}
                                    </p>
                                </div>

                                <div className="border-b border-white/10 px-4 py-4">
                                    <p className="text-[11px] uppercase tracking-[0.22em] text-slate-500">
                                        Organization
                                    </p>
                                    <p className="mt-2 text-sm text-slate-200">
                                        {user?.organization || "No Organization"}
                                    </p>
                                </div>

                                <div className="border-b border-white/10 px-4 py-4">
                                    <p className="text-[11px] uppercase tracking-[0.22em] text-slate-500">
                                        Cloud Account
                                    </p>
                                    <p className="mt-2 text-sm text-slate-200">
                                        {primaryAccount || "Connect Account"}
                                    </p>
                                </div>

                                <button
                                    onClick={handleLogout}
                                    className="flex w-full items-center gap-2 px-4 py-4 text-sm text-rose-300 transition hover:bg-rose-500/10"
                                >
                                    <LogOut size={16} />
                                    Logout
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Topbar;
