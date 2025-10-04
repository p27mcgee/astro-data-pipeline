package com.mcgeecahill.astro.processor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final ProcessingConfig processingConfig;

    @Bean(name = "imageProcessingExecutor")
    public Executor imageProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        ProcessingConfig.Parallel parallelConfig = processingConfig.getParallel();

        executor.setCorePoolSize(parallelConfig.getThreadPoolSize() / 2);
        executor.setMaxPoolSize(parallelConfig.getThreadPoolSize());
        executor.setQueueCapacity(parallelConfig.getQueueCapacity());
        executor.setThreadNamePrefix("ImageProcessor-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        // Custom rejection handler for graceful degradation
        executor.setRejectedExecutionHandler(new CustomRejectedExecutionHandler());

        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Initialized image processing executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    @Bean(name = "batchProcessingExecutor")
    public Executor batchProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        ProcessingConfig.Parallel parallelConfig = processingConfig.getParallel();

        executor.setCorePoolSize(parallelConfig.getMaxConcurrentJobs());
        executor.setMaxPoolSize(parallelConfig.getMaxConcurrentJobs() * 2);
        executor.setQueueCapacity(parallelConfig.getQueueCapacity() * 2);
        executor.setThreadNamePrefix("BatchProcessor-");
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);

        executor.setRejectedExecutionHandler(new CustomRejectedExecutionHandler());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Initialized batch processing executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return imageProcessingExecutor();
    }

    private static class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                log.warn("Task rejected by executor: {}. Queue size: {}, Active threads: {}",
                        r.getClass().getSimpleName(),
                        executor.getQueue().size(),
                        executor.getActiveCount());

                // Try to execute in caller thread as fallback
                try {
                    r.run();
                    log.info("Successfully executed rejected task in caller thread");
                } catch (Exception e) {
                    log.error("Failed to execute rejected task in caller thread", e);
                }
            }
        }
    }
}