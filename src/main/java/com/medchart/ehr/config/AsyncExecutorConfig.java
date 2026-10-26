package com.medchart.ehr.config;

import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * The @Async executor queue is bounded (spring.task.execution.pool.queue-capacity); when it is
 * full, work runs on the submitting thread instead of being dropped or queued without limit.
 */
@Configuration
public class AsyncExecutorConfig {

    @Bean
    public TaskExecutorCustomizer boundedTaskExecutorCustomizer() {
        return executor -> executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
