package com.middleware.manager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
    private final RateLimiter rateLimiter = new RateLimiter(clock);

    @Test
    @DisplayName("TC-01 窗口内请求数未超过阈值时全部放行")
    void withinLimitAllRequestsAllowed() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 5, 1000)).isTrue();
        }
    }

    @Test
    @DisplayName("TC-02 第 N 次请求放行，第 N+1 次请求被拒绝")
    void nPlusOneRequestRejected() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 5, 1000)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 5, 1000)).isFalse();
    }

    @Test
    @DisplayName("TC-03 并发请求下放行总数不超过阈值")
    void concurrentRequestsNeverExceedLimit() throws InterruptedException {
        int limit = 20;
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException ignored) {
                }
                if (rateLimiter.tryAcquire("download:concurrent", limit, 1000)) {
                    allowed.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(limit);
    }

    @Test
    @DisplayName("TC-04 窗口结束后自动恢复，无需重启或手动清理")
    void resetsAfterWindowElapses() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 3, 1000)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 3, 1000)).isFalse();

        clock.advance(Duration.ofMillis(1000));

        assertThat(rateLimiter.tryAcquire("download:1.2.3.4", 3, 1000)).isTrue();
    }

    @Test
    @DisplayName("TC-05 阈值与窗口时长参数可配置且各自独立生效")
    void limitAndWindowAreConfigurablePerCall() {
        for (int i = 0; i < 2; i++) {
            assertThat(rateLimiter.tryAcquire("forum:small-limit", 2, 500)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("forum:small-limit", 2, 500)).isFalse();

        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryAcquire("forum:big-limit", 10, 500)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("forum:big-limit", 10, 500)).isFalse();
    }

    @Test
    @DisplayName("TC-07 不同 key 之间限流计数互不影响，无法通过更换参数绕过限流")
    void differentKeysAreCountedIndependently() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire("download:same-client", 3, 1000)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("download:same-client", 3, 1000)).isFalse();

        assertThat(rateLimiter.tryAcquire("download:other-client", 3, 1000)).isTrue();
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
