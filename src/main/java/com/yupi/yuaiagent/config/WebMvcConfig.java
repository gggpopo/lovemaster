package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.tenant.TenantInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 注册租户拦截器
 */
@Configuration
@ConditionalOnProperty(name = "app.tenant.enabled", havingValue = "true")
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor())
                .addPathPatterns("/ai/**")  // 只拦截 AI 相关接口
                .order(0);  // 优先级最高
    }
}
