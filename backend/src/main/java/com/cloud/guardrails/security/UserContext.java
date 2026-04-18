package com.cloud.guardrails.security;

import java.util.List;

public class UserContext {

    private static final ThreadLocal<Long> orgIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> emailHolder = new ThreadLocal<>();

    public static void setOrgId(Long orgId) {
        orgIdHolder.set(orgId);
    }

    public static Long getOrgId() {
        return orgIdHolder.get();
    }

    public static void setEmail(String email) {
        emailHolder.set(email);
    }

    public static String getEmail() {
        return emailHolder.get();
    }

    private static final ThreadLocal<List<Long>> accountIdsHolder = new ThreadLocal<>();

    public static void setAccountIds(List<Long> ids) {
        accountIdsHolder.set(ids);
    }

    public static List<Long> getAccountIds() {
        return accountIdsHolder.get();
    }

    public static void clear() {
        orgIdHolder.remove();
        accountIdsHolder.remove();
        emailHolder.remove();
    }
}
