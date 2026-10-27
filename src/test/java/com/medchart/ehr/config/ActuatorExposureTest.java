package com.medchart.ehr.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorExposureTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            RabbitAutoConfiguration.class
    })
    @Import(SecurityConfig.class)
    static class ActuatorOnlyApp {
    }

    @Nested
    @SpringBootTest(classes = ActuatorOnlyApp.class)
    @AutoConfigureMockMvc
    class DefaultExposure {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void healthStaysPublic() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        void metricsIsNotPublished() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
            mockMvc.perform(get("/actuator/metrics/http.server.requests")).andExpect(status().isNotFound());
        }

        @Test
        void infoIsNotPublished() throws Exception {
            mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest(classes = ActuatorOnlyApp.class,
            properties = "management.endpoints.web.exposure.include=*")
    @AutoConfigureMockMvc
    class WhenExposureIsWidened {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void healthStaysPublic() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        void anonymousCallerCannotReadMetricsOrOtherEndpoints() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
            mockMvc.perform(get("/actuator/metrics/jvm.memory.used")).andExpect(status().isForbidden());
            mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
            mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "PROVIDER")
        void nonAdminUserCannotReadMetrics() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanReadMetrics() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        }
    }
}
