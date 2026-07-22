package com.middleware.manager.web.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.middleware.manager.service.ForumRateLimitProperties;
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

class ForumRateLimitInterceptorTest {

    @Mock
    private RateLimiter rateLimiter;

    private ForumRateLimitProperties properties;
    private ForumRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new ForumRateLimitProperties();
        properties.setEnabled(true);
        properties.setLimit(10);
        properties.setWindowSeconds(60);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        interceptor = new ForumRateLimitInterceptor(rateLimiter, properties, objectMapper);
    }

    @Test
    @DisplayName("TC-01 未超出阈值时正常放行论坛文章访问请求")
    void allowsRequestWithinLimit() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forum/posts/1");
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forum/posts/1");
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forum/posts/1");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("TC-06 编辑与删除文章（非只读访问）不计入访问限流")
    void nonGetRequestsAreNotRateLimited() throws Exception {
        MockHttpServletRequest putRequest = new MockHttpServletRequest("PUT", "/api/forum/posts/1");
        putRequest.setRemoteAddr("10.0.0.1");
        MockHttpServletRequest deleteRequest = new MockHttpServletRequest("DELETE", "/api/forum/posts/1");
        deleteRequest.setRemoteAddr("10.0.0.1");

        boolean putResult = interceptor.preHandle(putRequest, new MockHttpServletResponse(), new Object());
        boolean deleteResult = interceptor.preHandle(deleteRequest, new MockHttpServletResponse(), new Object());

        assertThat(putResult).isTrue();
        assertThat(deleteResult).isTrue();
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("TC-07 限流按来源客户端计数，更换文章 ID 不会绕过限流")
    void rateLimitKeyIsIndependentOfRequestedPost() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), eq(10), anyLong())).thenReturn(true);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/forum/posts/1");
        first.setRemoteAddr("10.0.0.1");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/forum/posts/999999");
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

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/forum/posts/1");
        first.setRemoteAddr("10.0.0.1");
        first.addHeader("X-Forwarded-For", "203.0.113.9");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/forum/posts/1");
        second.setRemoteAddr("10.0.0.1");
        second.addHeader("X-Forwarded-For", "198.51.100.7");
        interceptor.preHandle(second, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, times(2)).tryAcquire(keyCaptor.capture(), eq(10), anyLong());
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }
}
