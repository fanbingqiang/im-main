package com.ti.gateway.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.ti.IUserRPCService;

import java.util.List;

@Slf4j
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

    @Resource
    private GatewayAppProperties gatewayAppProperties;

    @Value("${tlivegateway.internal-secret:tlive-dev-secret}")
    private String internalSecret;

    @DubboReference
    private IUserRPCService userRPCService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isWhiteList(path)) {
            // 白名单路径无需鉴权, 但仍需注入内部通信密钥, 供下游 API 校验
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.set("X-Gateway-Secret", internalSecret);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        List<HttpCookie> httpCookies = request.getCookies().get("tltk");
        if (CollectionUtils.isEmpty(httpCookies)) {
            return unauthorized(exchange, "未登录");
        }
        String token = httpCookies.get(0).getValue();
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "token为空");
        }

        try {
            Long userId = userRPCService.getUserIdByToken(token);
            if (userId == null) {
                return unauthorized(exchange, "token无效或已过期");
            }
            // 清除客户端伪造的 X-User-Id, 注入认证后的用户ID与内部通信密钥
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.set("X-Gateway-Secret", internalSecret);
                        headers.set("X-User-Id", String.valueOf(userId));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.error("Token验证失败, path={}", path, e);
            return unauthorized(exchange, "token验证异常");
        }
    }

    private boolean isWhiteList(String path) {
        List<String> whiteList = gatewayAppProperties.getWhiteList();
        if (CollectionUtils.isEmpty(whiteList)) {
            return false;
        }
        for (String pattern : whiteList) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"message\":\"" + message + "\"}";
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
