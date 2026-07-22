package com.middleware.manager.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RateLimiter {

    private static final int MAX_ENTRIES = 10_000;
    private static final int EXPIRY_GRACE_MULTIPLIER = 2;

    private final Clock clock;
    private final int maxEntries;
    private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);

    public RateLimiter() {
        this(Clock.systemUTC(), MAX_ENTRIES);
    }

    RateLimiter(Clock clock) {
        this(clock, MAX_ENTRIES);
    }

    RateLimiter(Clock clock, int maxEntries) {
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    public synchronized boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || now - window.startMillis >= window.windowMillis) {
            window = new Window(now, windowMillis);
            windows.put(key, window);
            evictExpired(now);
        }
        if (window.count >= limit) {
            return false;
        }
        window.count++;
        return true;
    }

    int trackedKeyCount() {
        synchronized (this) {
            return windows.size();
        }
    }

    private void evictExpired(long now) {
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            if (now - window.startMillis >= window.windowMillis * EXPIRY_GRACE_MULTIPLIER) {
                iterator.remove();
            }
        }
        while (windows.size() > maxEntries) {
            iterator = windows.values().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Window {
        final long windowMillis;
        long startMillis;
        int count;

        Window(long startMillis, long windowMillis) {
            this.startMillis = startMillis;
            this.windowMillis = windowMillis;
        }
    }
}
