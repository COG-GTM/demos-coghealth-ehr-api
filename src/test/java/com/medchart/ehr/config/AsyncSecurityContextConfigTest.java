package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncSecurityContextConfigTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void asyncTaskSeesCallerAuthenticationAndPoolThreadIsClearedAfterwards() throws Exception {
        executor = executor(new AsyncSecurityContextConfig().securityContextTaskDecorator());
        Authentication bob = new UsernamePasswordAuthenticationToken("bob.clerk", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(bob);

        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(currentName()));
        assertThat(seen.get(5, TimeUnit.SECONDS)).isEqualTo("bob.clerk");

        SecurityContextHolder.clearContext();
        CompletableFuture<String> later = new CompletableFuture<>();
        executor.execute(() -> later.complete(currentName()));
        assertThat(later.get(5, TimeUnit.SECONDS)).isEqualTo("<none>");
    }

    private static String currentName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "<none>" : auth.getName();
    }

    private static ThreadPoolTaskExecutor executor(TaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(decorator);
        executor.initialize();
        return executor;
    }
}
