package com.copilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs the document ingestion pipeline (@Async in IngestionService). A bounded pool
 * so a burst of uploads can't exhaust the JVM's threads — excess work queues instead.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "ingestionExecutor")
    public ThreadPoolTaskExecutor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
    }
}
