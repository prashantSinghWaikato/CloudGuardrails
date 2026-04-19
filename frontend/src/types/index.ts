export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface PageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    number: number;
    size: number;
}

export interface Violation {
    id: number;
    ruleName: string;
    resourceId: string;
    severity: Severity;
    status: string;
    accountId: string;
}

export interface Remediation {
    id: number;
    action: string;
    status: string;
    attemptCount: number;
    executedAt: string | null;
    lastVerifiedAt: string | null;
    ruleName: string;
    resourceId: string;
    targetAccountId: string | null;
    targetResourceId: string | null;
    lastTriggeredBy: string | null;
    lastTriggerSource: string | null;
    verificationStatus: string | null;
    verificationMessage: string | null;
    response?: Record<string, unknown> | null;
    history: RemediationExecution[];
}

export interface RemediationExecution {
    id: number;
    action: string;
    triggerSource: string | null;
    triggeredBy: string | null;
    attemptNumber: number | null;
    targetAccountId: string | null;
    targetResourceId: string | null;
    status: string | null;
    verificationStatus: string | null;
    verificationMessage: string | null;
    startedAt: string | null;
    completedAt: string | null;
    response: Record<string, unknown> | null;
}

export interface ViolationDetail {
    id: number;
    ruleName: string;
    severity: Severity;
    status: string;
    resourceId: string;
    accountId: string;
    provider: string | null;
    region: string | null;
    organizationName: string | null;
    createdAt: string | null;
    updatedAt: string | null;
    eventType: string | null;
    externalEventId: string | null;
    eventTimestamp: string | null;
    payload: Record<string, unknown> | null;
    remediation: Remediation | null;
}

export interface Notification {
    id: number;
    type: string;
    title: string;
    message: string;
    severity: string;
    resourceId: string;
    read: boolean;
    createdAt: string;
    violationId: number | null;
    remediationId: number | null;
}

export interface Rule {
    id: number;
    ruleName: string;
    description: string;
    severity: Severity;
    enabled: boolean;
    autoRemediation: boolean;
    remediationAction: string;
}

export interface RuleUpdateRequest {
    description: string;
    severity: Severity;
    enabled: boolean;
    autoRemediation: boolean;
    remediationAction: string;
}

export interface CloudAccount {
    id: number;
    name: string;
    accountId: string;
    provider: string;
    region: string;
    monitoringEnabled: boolean;
    activationStatus: string;
    activationMethod: string | null;
    roleArn: string | null;
    lastActivatedAt: string | null;
    lastSyncAt: string | null;
    lastSyncStatus: string | null;
    lastSyncMessage: string | null;
    lastScanEventsSeen: number | null;
    lastScanEventsIngested: number | null;
    lastScanDuplicatesSkipped: number | null;
    lastScanViolationsCreated: number | null;
    lastScanPostureFindingsCreated: number | null;
}

export interface AccountScanRun {
    id: number;
    startedAt: string | null;
    completedAt: string | null;
    status: string | null;
    message: string | null;
    eventsSeen: number | null;
    eventsIngested: number | null;
    duplicatesSkipped: number | null;
    violationsCreated: number | null;
    postureFindingsCreated: number | null;
}

export interface AccountFormData {
    name: string;
    accountId: string;
    provider: string;
    region: string;
    accessKey: string;
    secretKey: string;
}

export interface AccountValidationResponse {
    valid: boolean;
    provider: string;
    accountId: string;
    arn: string;
    userId: string;
    message: string;
}

export interface AccountActivationFormData {
    roleArn: string;
    externalId: string;
}
