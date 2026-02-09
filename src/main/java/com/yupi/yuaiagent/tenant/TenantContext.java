package com.yupi.yuaiagent.tenant;

/**
 * 租户上下文
 * <p>
 * 使用 ThreadLocal 存储当前请求的租户信息
 */
public class TenantContext {

    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前租户信息
     */
    public static void setTenant(String tenantId, String userId) {
        CONTEXT.set(new TenantInfo(tenantId, userId));
    }

    /**
     * 获取当前租户信息
     */
    public static TenantInfo getTenant() {
        return CONTEXT.get();
    }

    /**
     * 获取当前租户 ID
     */
    public static String getTenantId() {
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getTenantId() : null;
    }

    /**
     * 获取当前用户 ID
     */
    public static String getUserId() {
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getUserId() : null;
    }

    /**
     * 清除当前租户信息
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 租户信息
     */
    public static class TenantInfo {
        private final String tenantId;
        private final String userId;

        public TenantInfo(String tenantId, String userId) {
            this.tenantId = tenantId;
            this.userId = userId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUserId() {
            return userId;
        }

        /**
         * 获取用于限流的唯一标识
         */
        public String getRateLimitKey() {
            if (tenantId != null && userId != null) {
                return tenantId + ":" + userId;
            } else if (tenantId != null) {
                return tenantId;
            } else if (userId != null) {
                return userId;
            }
            return "anonymous";
        }
    }
}
