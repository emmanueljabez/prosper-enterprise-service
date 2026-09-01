package com.prosper.prospermentor.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class B2BDemoRequestThrottleService {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_NETWORK = 20;
    private static final int MAX_REQUESTS_PER_EMAIL = 3;

    private final Map<String, RequestWindow> requestWindows = new HashMap<>();

    public synchronized void assertAllowed(String clientAddress, String workEmail) {
        Instant now = Instant.now();
        removeExpiredWindows(now);

        assertKeyAllowed(
                "ip:" + normalizeKey(clientAddress, "unknown"),
                MAX_REQUESTS_PER_NETWORK,
                "Too many demo requests from this network. Please try again later.",
                now
        );

        assertKeyAllowed(
                "email:" + normalizeKey(workEmail, "unknown"),
                MAX_REQUESTS_PER_EMAIL,
                "Too many demo requests for this work email. Please try again later.",
                now
        );
    }

    private void assertKeyAllowed(String key, int maxRequests, String message, Instant now) {
        RequestWindow window = requestWindows.get(key);
        if (window == null || window.startedAt.plus(WINDOW).isBefore(now)) {
            requestWindows.put(key, new RequestWindow(now, 1));
            return;
        }

        if (window.count >= maxRequests) {
            throw new TooManyDemoRequestsException(message);
        }

        window.count++;
    }

    private void removeExpiredWindows(Instant now) {
        requestWindows.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(WINDOW).isBefore(now));
    }

    private String normalizeKey(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class RequestWindow {
        private final Instant startedAt;
        private int count;

        private RequestWindow(Instant startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }

    public static class TooManyDemoRequestsException extends RuntimeException {
        public TooManyDemoRequestsException(String message) {
            super(message);
        }
    }
}
