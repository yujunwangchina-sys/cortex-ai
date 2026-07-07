package com.ruoyi.agent.runtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent runtime thread pool configuration.
 *
 * Three pools:
 *   1. agentIoExecutor  - I/O bound (background skill review, memory extraction, watchdog)
 *   2. agentStreamExecutor - stream reading (process stdout/stderr, SSE)
 *   3. agentWatchdogExecutor - scheduled watchdog tasks (single thread, daemon)
 *
 * @author ruoyi
 */
@Configuration
public class AgentThreadPoolConfig
{
    private static final Logger log = LoggerFactory.getLogger(AgentThreadPoolConfig.class);

    /**
     * I/O executor: background LLM calls (skill review, memory extraction).
     * These are long-running, low-concurrency tasks.
     */
    @Bean(name = "agentIoExecutor")
    public ThreadPoolTaskExecutor agentIoExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(300);
        executor.setThreadNamePrefix("agent-io-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Agent I/O thread pool initialized [core=4, max=16, queue=200]");
        return executor;
    }

    /**
     * Stream executor: process stdout/stderr reading.
     * Short-lived, high-throughput, always paired (2 threads per execution).
     */
    @Bean(name = "agentStreamExecutor")
    public ThreadPoolTaskExecutor agentStreamExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("agent-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("Agent stream thread pool initialized [core=8, max=32, queue=100]");
        return executor;
    }

    /**
     * Watchdog executor: single-thread daemon for SSE stale detection.
     */
    @Bean(name = "agentWatchdogExecutor")
    public ThreadPoolTaskExecutor agentWatchdogExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("agent-watchdog-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        log.info("Agent watchdog thread pool initialized [core=1, max=4]");
        return executor;
    }
}