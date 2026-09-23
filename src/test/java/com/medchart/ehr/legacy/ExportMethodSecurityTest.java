package com.medchart.ehr.legacy;

import com.medchart.ehr.config.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.persistence.EntityManager;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The export services deny callers without the privileged role even when the
 * request never passes through the web layer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MethodSecurityConfig.class, ExportMethodSecurityTest.TestBeans.class})
class ExportMethodSecurityTest {

    @Configuration
    static class TestBeans {

        @Bean
        EntityManager entityManager() {
            return Mockito.mock(EntityManager.class);
        }

        @Bean
        EncounterExportService encounterExportService() {
            return new EncounterExportService();
        }

        @Bean
        ReportGenerator reportGenerator() {
            return new ReportGenerator();
        }
    }

    @Autowired
    private EncounterExportService encounterExportService;

    @Autowired
    private ReportGenerator reportGenerator;

    @Test
    void unauthenticatedCallerCannotExport() {
        assertThatThrownBy(() -> reportGenerator.generateDailyReport(0, 10))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerCannotExport() {
        assertThatThrownBy(() -> encounterExportService.exportPatientEncounterHistory(1L, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonPrivilegedRoleCannotExport() {
        assertThatThrownBy(() -> encounterExportService.exportEncountersForDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), 0, 10))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> reportGenerator.generatePatientRoster(0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }
}
