package com.medchart.ehr.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * JPA slice against the Flyway-migrated Testcontainers Postgres (no embedded DB replacement).
 * Each test runs in a transaction that is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractJpaTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        TestContainers.register(registry);
    }
}
