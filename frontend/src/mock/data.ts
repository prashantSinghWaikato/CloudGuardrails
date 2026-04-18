import type { Violation } from "../types";

export const violations: Violation[] = [
    {
        id: 1,
        ruleName: "SSH Open to Internet",
        resourceId: "sg-123",
        severity: "CRITICAL",
        status: "OPEN",
        accountId: "AWS Prod",
    },
    {
        id: 2,
        ruleName: "S3 Public Access",
        resourceId: "bucket-456",
        severity: "HIGH",
        status: "OPEN",
        accountId: "AWS Dev",
    },
];
