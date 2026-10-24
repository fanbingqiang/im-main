package com.it.live.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 校验网关内部通信密钥, 防止客户端绕过网关直连 API 伪造 X-User-Id
 * 所有请求必须经过网关转发 (网关会注入 X-Gateway-Secret)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewaySecretFilter implements Filter {

    @Value("${ti.gateway.internal-secret:tlive-dev-secret}")
    private String internalSecret;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String secret = httpRequest.getHeader("X-Gateway-Secret");
        if (internalSecret == null || internalSecret.isEmpty() || !internalSecret.equals(secret)) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(401);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"请求必须通过网关转发\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
