package com.cortex.agent.runtime.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 重试处理器 — 参考 hermes retry_utils.py 的 jittered backoff
 *
 * @author cortex
 */
public class RetryHandler
{
    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;

    public RetryHandler()
    {
        this(3, 2000, 30000);
    }

    public RetryHandler(int maxRetries, long baseDelayMs, long maxDelayMs)
    {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    /**
     * 带重试执行
     * @param action 要执行的动作
     */
    public <T> T executeWithRetry(Supplier<T> action, java.util.function.Predicate<Exception> shouldRetry)
    {
        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++)
        {
            try
            {
                return action.get();
            }
            catch (Exception e)
            {
                lastError = e;
                if (attempt >= maxRetries || !shouldRetry.test(e))
                {
                    break;
                }
                long delay = jitteredBackoff(attempt + 1);
                log.warn("LLM调用失败, {}ms后重试(attempt {}/{})",
                        delay, attempt + 1, maxRetries, e);
                try
                {
                    Thread.sleep(delay);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new RuntimeException("LLM调用重试耗尽", lastError);
    }

    /**
     * 抖动指数退避
     */
    private long jitteredBackoff(int attempt)
    {
        long delay = (long) (baseDelayMs * Math.pow(2, attempt - 1));
        delay = Math.min(delay, maxDelayMs);
        // 添加 0~50% 的抖动
        long jitter = ThreadLocalRandom.current().nextLong(0, delay / 2 + 1);
        return delay + jitter;
    }
}
