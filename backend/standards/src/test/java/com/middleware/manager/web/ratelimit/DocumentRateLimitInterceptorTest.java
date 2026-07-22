package com.middleware.manager.web.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.middleware.manager.service.DocumentRateLimitProperties;
import com.middleware.manager.service.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRateLimitInterceptorTest {

    @Mock
    private RateLimiter rateLimiter;

    private DocumentRateLimitProperties properties;
    private DocumentRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new DocumentRateLimitProperties();
        properties.setEnabled(true);
        properties.setLimit(10);
        properties.setWindowSeconds(60);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        interceptor = new DocumentRateLimitInterceptor(rateLimiter, properties, objectMapper);
    }

    @Test
    @DisplayName("TC-01 未超出阈值时正常放行标准文档访问请求")
    void allowsRequestWithinLimit() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/standards/1");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-02 达到阈值后返回 429 并附带可识别的限流提示")
    void rejectsWithTooManyRequestsWhenLimitExceeded() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/standards/preview");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("TC-05 限流开关关闭时不生效，请求直接放行")
    void bypassesWhenDisabled() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/standards/raw");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("TC-07 限流按来源客户端计数，更换文档/文件参数不会绕过限流")
    void rateLimitKeyIsIndependentOfRequestedDocument() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(true);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/public/standards/1");
        first.setRemoteAddr("10.0.0.1");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/public/standards/999999");
        second.setRemoteAddr("10.0.0.1");
        interceptor.preHandle(second, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, times(2)).tryAcquire(keyCaptor.capture(), eq(10), anyLong());
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("TC-07 伪造 X-Forwarded-For 请求头不能绕过按客户端的限流")
    void forgedForwardedForHeaderDoesNotBypassRateLimit() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(true);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/public/standards/1");
        first.setRemoteAddr("10.0.0.1");
        first.addHeader("X-Forwarded-For", "203.0.113.9");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/public/standards/1");
        second.setRemoteAddr("10.0.0.1");
        second.addHeader("X-Forwarded-For", "198.51.100.7");
        interceptor.preHandle(second, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, times(2))
                .tryAcquire(keyCaptor.capture(), eq(10), anyLong());
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }
}
