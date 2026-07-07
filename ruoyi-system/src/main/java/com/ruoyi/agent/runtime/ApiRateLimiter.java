package com.ruoyi.agent.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window rate limiter for third-party widget API calls.
 * Keyed by API key; internal calls (no key) are never limited.
 */
@Component
public class ApiRateLimiter
{
    @Value("${cortex.widget.rate-limit:60}")
    private int maxRequests;

    @Value("${cortex.widget.rate-window-ms:60000}")
    private long windowMs;

    private static class Bucket
    {
        long windowStart;
        int count;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Try to acquire one request slot for the given key.
     *
     * @param key usually the API key; null/empty means internal call (always allowed)
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean tryAcquire(String key)
    {
        if (key == null || key.trim().isEmpty())
        {
            return true;
        }
        long now = System.currentTimeMillis();
        Bucket b = buckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs)
            {
                Bucket nb = new Bucket();
                nb.windowStart = now;
                nb.count = 1;
                return nb;
            }
            existing.count++;
            return existing;
        });
        return b.count <= maxRequests;
    }
}