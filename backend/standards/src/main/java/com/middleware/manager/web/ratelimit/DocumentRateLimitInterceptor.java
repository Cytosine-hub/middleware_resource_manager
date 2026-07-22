package com.middleware.manager.web.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.service.DocumentRateLimitProperties;
import com.middleware.manager.service.RateLimiter;
import com.middleware.manager.web.api.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class DocumentRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final DocumentRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public DocumentRateLimitInterceptor(RateLimiter rateLimiter, DocumentRateLimitProperties properties,
                                         ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }
        String key = "document:" + clientId(request);
        boolean allowed = rateLimiter.tryAcquire(key, properties.getLimit(), properties.getWindowSeconds() * 1000);
        if (allowed) {
            return true;
        }
        log.warn("标准文档访问接口触发限流 client={} uri={}", key, request.getRequestURI());
        writeTooManyRequests(response);
        return false;
    }

    private String clientId(HttpServletRequest request) {
        // 只信任连接层的 remoteAddr；X-Forwarded-For 等请求头由客户端自行携带，可伪造/轮换，
        // 用于限流分组会被绕过，因此不采信。
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiError error = new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.RATE_LIMIT_EXCEEDED, ErrorMessages.RATE_LIMIT_EXCEEDED);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
