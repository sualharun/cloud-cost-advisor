package com.microsoft.cloudoptimizer.security;

/**
 * Thread-local tenant context for multi-tenant request handling.
 *
 * MULTI-TENANCY DESIGN:
 * Each API request is associated with a tenant (Azure AD tenant or organization).
 * The tenant ID is extracted from the JWT token and stored in thread-local storage.
 *
 * USAGE:
 * - Set by JwtAuthenticationFilter after token validation
 * - Accessed by services to scope data queries
 * - Cleared after request completion
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String[]> CURRENT_ROLES = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available");
        }
        return tenantId;
    }

    public static String getCurrentTenantIdOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentUser(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void setCurrentRoles(String[] roles) {
        CURRENT_ROLES.set(roles);
    }

    public static String[] getCurrentRoles() {
        return CURRENT_ROLES.get();
    }

    public static boolean hasRole(String role) {
        String[] roles = CURRENT_ROLES.get();
        if (roles == null) return false;
        for (String r : roles) {
            if (r.equals(role)) return true;
        }
        return false;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        CURRENT_ROLES.remove();
    }
}
