package com.medchart.ehr.audit;

import com.medchart.ehr.config.AsyncSecurityContextConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AsyncAuditAttributionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class, AopAutoConfiguration.class))
            .withUserConfiguration(AuditWiring.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void asyncAuditedMethodIsAttributedToCallerOnSpringManagedExecutor() {
        runner.withUserConfiguration(AsyncSecurityContextConfig.class).run(context -> {
            AuditEvent event = callAsBob(context.getBean(AsyncAlerts.class), context.getBean(AuditEventRepository.class));

            assertThat(event.getUserId()).isEqualTo("bob.clerk");
            assertThat(event.getRequestDetails()).isEqualTo("authorities=ROLE_STAFF");
            assertThat(event.getPatientId()).isEqualTo(42L);
        });
    }

    @Test
    void withoutDecoratorAsyncAuditFallsBackToSystem() {
        runner.run(context -> {
            AuditEvent event = callAsBob(context.getBean(AsyncAlerts.class), context.getBean(AuditEventRepository.class));

            assertThat(event.getUserId()).isEqualTo("system");
        });
    }

    private static AuditEvent callAsBob(AsyncAlerts alerts, AuditEventRepository repository) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bob.clerk", null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));
        String callerThread = Thread.currentThread().getName();

        String workerThread = alerts.sendAlert(7L, 42L).get(5, TimeUnit.SECONDS);
        assertThat(workerThread).isNotEqualTo(callerThread);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository, timeout(5000)).save(captor.capture());
        return captor.getValue();
    }

    @Configuration
    @EnableAsync
    @Import({AuditAspect.class, AuditService.class})
    static class AuditWiring {

        @Bean
        AuditEventRepository auditEventRepository() {
            return mock(AuditEventRepository.class);
        }

        @Bean
        AsyncAlerts asyncAlerts() {
            return new AsyncAlerts();
        }
    }

    static class AsyncAlerts {

        @Async
        @AuditAccess(action = AuditAction.CREATE, resourceType = "CriticalAlert", description = "Send critical alert")
        public CompletableFuture<String> sendAlert(Long providerId, @PatientId Long patientId) {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
