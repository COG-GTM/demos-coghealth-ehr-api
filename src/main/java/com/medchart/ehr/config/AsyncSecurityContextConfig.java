package com.medchart.ehr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Carries the caller's SecurityContext onto {@code @Async} threads so audit events written
 * there (e.g. critical and non-adherence alerts) are attributed to the user who triggered them.
 * Picked up by Spring Boot's auto-configured {@code applicationTaskExecutor}.
 */
@Configuration
public class AsyncSecurityContextConfig {

    @Bean
    public TaskDecorator securityContextTaskDecorator() {
        return AsyncSecurityContextConfig::withCallerSecurityContext;
    }

    static Runnable withCallerSecurityContext(Runnable task) {
        SecurityContext callerContext = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(callerContext);
            try {
                task.run();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }
}
