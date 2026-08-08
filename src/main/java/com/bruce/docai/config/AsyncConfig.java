package com.bruce.docai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables asynchronous document ingestion. Uploads return immediately while a
 * bounded worker pool performs Tika parsing and Ollama embedding off the request
 * thread. The pool size intentionally bounds how many ingestions (and therefore
 * DB connections held during embedding) run concurrently, protecting the web
 * request thread pool and the datasource connection pool under load.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DOCUMENT_INGEST_EXECUTOR = "docIngestExecutor";

    @Value("${app.ingest.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.ingest.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${app.ingest.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = DOCUMENT_INGEST_EXECUTOR)
    public Executor documentIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("doc-ingest-");
        // When the queue is full, run on the caller thread rather than dropping
        // an upload, applying natural backpressure to producers.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
