package com.yupi.yuaiagent.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器
 * <p>
 * 从请求头中提取租户 ID 和用户 ID，设置到 TenantContext 中
 */
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        String userId = request.getHeader(HEADER_USER_ID);

        // 如果没有提供租户 ID，尝试从请求参数获取
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = request.getParameter("tenantId");
        }

        // 如果没有提供用户 ID，尝试从请求参数获取
        if (userId == null || userId.isEmpty()) {
            userId = request.getParameter("userId");
        }

        // 设置租户上下文
        TenantContext.setTenant(tenantId, userId);

        if (log.isDebugEnabled()) {
            log.debug("Tenant context set - tenantId: {}, userId: {}", tenantId, userId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理租户上下文，防止内存泄漏
        TenantContext.clear();
    }
}
