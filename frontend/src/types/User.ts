export type CloudAccount = {
    id: number;
    accountId: string;
    provider: string;
    region: string;
    monitoringEnabled?: boolean;
    activationStatus?: string;
};

export type Organization = {
    id: number;
    name: string;
};

export type User = {
    id: number;
    name: string;
    email: string;
    organization: Organization;
    cloudAccounts: CloudAccount[];
    role: "ADMIN" | "USER";
};
