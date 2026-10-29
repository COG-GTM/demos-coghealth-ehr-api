package com.medchart.ehr.security;

import com.medchart.ehr.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderSecretTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JwtTokenProvider.class)
            .withPropertyValues("medchart.security.jwt.expiration=60000");

    @Test
    void rejectsShortSecret() {
        runner.withPropertyValues("medchart.security.jwt.secret=dev-secret-key-change-in-production")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsStrongSecret() {
        runner.withPropertyValues("medchart.security.jwt.secret=" + ApiAuthenticationTest.JWT_SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
