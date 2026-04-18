import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import type {
    Notification,
    Remediation,
    RemediationExecution,
    Violation,
} from "../types";
import { API_BASE_URL } from "./api";

let client: Client | null = null;
let violationUnsubscribe: (() => void) | null = null;
let remediationUnsubscribe: (() => void) | null = null;
let notificationUnsubscribe: (() => void) | null = null;

const violationListeners = new Set<(msg: Violation) => void>();
const remediationListeners = new Set<(msg: Remediation) => void>();
const notificationListeners = new Set<(msg: Notification) => void>();

type RawViolation = Violation & {
    rule?: {
        ruleName?: string;
    };
};

type RawRemediation = Remediation & {
    violation?: {
        resourceId?: string;
        rule?: {
            ruleName?: string;
        };
    };
    history?: RawRemediationExecution[];
};

type RawRemediationExecution = RemediationExecution;

const normalizeRemediationExecution = (
    execution: RawRemediationExecution
): RemediationExecution => ({
    id: execution.id,
    action: execution.action ?? "UNKNOWN",
    triggerSource: execution.triggerSource ?? null,
    triggeredBy: execution.triggeredBy ?? null,
    attemptNumber: execution.attemptNumber ?? null,
    targetAccountId: execution.targetAccountId ?? null,
    targetResourceId: execution.targetResourceId ?? null,
    status: execution.status ?? null,
    verificationStatus: execution.verificationStatus ?? null,
    verificationMessage: execution.verificationMessage ?? null,
    startedAt: execution.startedAt ?? null,
    completedAt: execution.completedAt ?? null,
    response:
        typeof execution.response === "object" && execution.response !== null
            ? (execution.response as Record<string, unknown>)
            : null,
});

const normalizeViolation = (message: RawViolation): Violation => ({
    id: message.id,
    ruleName: message.ruleName ?? message.rule?.ruleName ?? "Unknown",
    resourceId: message.resourceId ?? "N/A",
    severity: message.severity ?? "LOW",
    status: message.status ?? "OPEN",
    accountId: message.accountId ?? "N/A",
});

const normalizeRemediation = (message: RawRemediation): Remediation => ({
    id: message.id,
    action: message.action ?? "UNKNOWN",
    status: message.status ?? "UNKNOWN",
    attemptCount: message.attemptCount ?? 0,
    executedAt: message.executedAt ?? null,
    lastVerifiedAt: message.lastVerifiedAt ?? null,
    ruleName:
        message.ruleName ?? message.violation?.rule?.ruleName ?? "Unknown",
    resourceId: message.resourceId ?? message.violation?.resourceId ?? "N/A",
    targetAccountId: message.targetAccountId ?? null,
    targetResourceId: message.targetResourceId ?? null,
    lastTriggeredBy: message.lastTriggeredBy ?? null,
    lastTriggerSource: message.lastTriggerSource ?? null,
    verificationStatus: message.verificationStatus ?? null,
    verificationMessage: message.verificationMessage ?? null,
    response:
        typeof message.response === "object" && message.response !== null
            ? (message.response as Record<string, unknown>)
            : null,
    history: Array.isArray(message.history)
        ? message.history.map(normalizeRemediationExecution)
        : [],
});

const notifyViolationListeners = (message: RawViolation) => {
    const normalized = normalizeViolation(message);
    violationListeners.forEach((listener) => listener(normalized));
};

const notifyRemediationListeners = (message: RawRemediation) => {
    const normalized = normalizeRemediation(message);
    remediationListeners.forEach((listener) => listener(normalized));
};

const notifyNotificationListeners = (message: Notification) => {
    notificationListeners.forEach((listener) => listener(message));
};

const deactivateIfUnused = () => {
    if (
        client &&
        violationListeners.size === 0 &&
        remediationListeners.size === 0 &&
        notificationListeners.size === 0
    ) {
        violationUnsubscribe?.();
        remediationUnsubscribe?.();
        notificationUnsubscribe?.();
        violationUnsubscribe = null;
        remediationUnsubscribe = null;
        notificationUnsubscribe = null;
        client.deactivate();
        client = null;
    }
};

const ensureSubscriptions = () => {
    if (!client) {
        return;
    }

    if (!violationUnsubscribe) {
        violationUnsubscribe = client.subscribe(
            "/topic/violations",
            (message) => {
                try {
                    notifyViolationListeners(JSON.parse(message.body) as RawViolation);
                } catch (error) {
                    console.error("Violation parse error", error);
                }
            }
        ).unsubscribe;
    }

    if (!remediationUnsubscribe) {
        remediationUnsubscribe = client.subscribe(
            "/topic/remediations",
            (message) => {
                try {
                    notifyRemediationListeners(
                        JSON.parse(message.body) as RawRemediation
                    );
                } catch (error) {
                    console.error("Remediation parse error", error);
                }
            }
        ).unsubscribe;
    }

    if (!notificationUnsubscribe) {
        notificationUnsubscribe = client.subscribe(
            "/topic/notifications",
            (message) => {
                try {
                    notifyNotificationListeners(JSON.parse(message.body) as Notification);
                } catch (error) {
                    console.error("Notification parse error", error);
                }
            }
        ).unsubscribe;
    }
};

export const connectWebSocket = (
    onViolation?: (msg: Violation) => void,
    onRemediation?: (msg: Remediation) => void,
    onNotification?: (msg: Notification) => void
) => {
    if (onViolation) {
        violationListeners.add(onViolation);
    }

    if (onRemediation) {
        remediationListeners.add(onRemediation);
    }

    if (onNotification) {
        notificationListeners.add(onNotification);
    }

    if (!client) {
        const token = localStorage.getItem("token");

        client = new Client({
            webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws?token=${token}`),

            reconnectDelay: 5000,

            debug: () => {},

            onConnect: () => {
                ensureSubscriptions();
            },

            onStompError: (frame) => {
                console.error("STOMP error:", frame.headers["message"]);
            },

            onWebSocketError: (error) => {
                console.error("WebSocket error:", error);
            },
        });

        client.activate();
    } else if (client.connected) {
        ensureSubscriptions();
    }

    return () => {
        if (onViolation) {
            violationListeners.delete(onViolation);
        }

        if (onRemediation) {
            remediationListeners.delete(onRemediation);
        }

        if (onNotification) {
            notificationListeners.delete(onNotification);
        }

        deactivateIfUnused();
    };
};

export const disconnectWebSocket = () => {
    violationListeners.clear();
    remediationListeners.clear();
    notificationListeners.clear();
    deactivateIfUnused();
};
