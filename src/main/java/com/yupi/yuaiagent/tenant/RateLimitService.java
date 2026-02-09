package com.yupi.yuaiagent.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流服务
 * <p>
 * 基于滑动窗口的限流实现，支持 Redis 和本地内存两种模式
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.tenant.enabled", havingValue = "true")
public class RateLimitService {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    @Resource(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.tenant.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.tenant.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${app.memory.redis.enabled:false}")
    private boolean redisEnabled;

    // 本地限流计数器（Redis 不可用时使用）
    private final ConcurrentHashMap<String, RateLimitCounter> localCounters = new ConcurrentHashMap<>();

    /**
     * 检查是否允许请求
     *
     * @param key 限流 key（通常是 tenantId:userId）
     * @return true 允许请求，false 被限流
     */
    public boolean allowRequest(String key) {
        if (!rateLimitEnabled) {
            return true;
        }

        if (redisEnabled && redisTemplate != null) {
            return allowRequestWithRedis(key);
        } else {
            return allowRequestWithLocal(key);
        }
    }

    /**
     * 获取剩余请求次数
     */
    public int getRemainingRequests(String key) {
        if (!rateLimitEnabled) {
            return Integer.MAX_VALUE;
        }

        if (redisEnabled && redisTemplate != null) {
            return getRemainingRequestsWithRedis(key);
        } else {
            return getRemainingRequestsWithLocal(key);
        }
    }

    /**
     * 基于 Redis 的限流检查
     */
    private boolean allowRequestWithRedis(String key) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - 60000; // 1 分钟窗口

        try {
            // 移除窗口外的记录
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

            // 获取当前窗口内的请求数
            Long count = redisTemplate.opsForZSet().zCard(redisKey);
            if (count == null) {
                count = 0L;
            }

            if (count >= requestsPerMinute) {
                log.warn("Rate limit exceeded for key: {}, count: {}", key, count);
                return false;
            }

            // 添加当前请求
            redisTemplate.opsForZSet().add(redisKey, String.valueOf(currentTime), currentTime);
            redisTemplate.expire(redisKey, 2, TimeUnit.MINUTES);

            return true;
        } catch (Exception e) {
            log.error("Redis rate limit check failed for key: {}", key, e);
            // Redis 异常时降级到本地限流
            return allowRequestWithLocal(key);
        }
    }

    /**
     * 基于本地内存的限流检查
     */
    private boolean allowRequestWithLocal(String key) {
        RateLimitCounter counter = localCounters.computeIfAbsent(key, k -> new RateLimitCounter());
        return counter.allowRequest(requestsPerMinute);
    }

    /**
     * 获取 Redis 中的剩余请求次数
     */
    private int getRemainingRequestsWithRedis(String key) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long windowStart = System.currentTimeMillis() - 60000;

        try {
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
            Long count = redisTemplate.opsForZSet().zCard(redisKey);
            if (count == null) {
                count = 0L;
            }
            return Math.max(0, requestsPerMinute - count.intValue());
        } catch (Exception e) {
            log.error("Failed to get remaining requests from Redis for key: {}", key, e);
            return getRemainingRequestsWithLocal(key);
        }
    }

    /**
     * 获取本地的剩余请求次数
     */
    private int getRemainingRequestsWithLocal(String key) {
        RateLimitCounter counter = localCounters.get(key);
        if (counter == null) {
            return requestsPerMinute;
        }
        return counter.getRemainingRequests(requestsPerMinute);
    }

    /**
     * 本地限流计数器
     */
    private static class RateLimitCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        public synchronized boolean allowRequest(int limit) {
            long now = System.currentTimeMillis();
            // 如果超过 1 分钟，重置窗口
            if (now - windowStart > 60000) {
                windowStart = now;
                count.set(0);
            }

            if (count.get() >= limit) {
                return false;
            }

            count.incrementAndGet();
            return true;
        }

        public int getRemainingRequests(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60000) {
                return limit;
            }
            return Math.max(0, limit - count.get());
        }
    }
}
