package com.aichat.util;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class RateLimiter {
    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<UUID, Deque<Long>> requests = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    public boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requests.computeIfAbsent(playerId, k -> new ConcurrentLinkedDeque<>());
        // Expire old entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() < now - windowMillis) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxRequests) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    public void cleanup(UUID playerId) {
        requests.remove(playerId);
    }
}
