package com.middleware.manager.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int limit, long windowMillis) {
        Window window = windows.computeIfAbsent(key, k -> new Window(clock.millis()));
        synchronized (window) {
            long now = clock.millis();
            if (now - window.startMillis >= windowMillis) {
                window.startMillis = now;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        long startMillis;
        int count;

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
