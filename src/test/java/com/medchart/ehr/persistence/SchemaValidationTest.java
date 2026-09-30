package com.medchart.ehr.persistence;

import com.medchart.ehr.MedchartEhrApplication;
import com.medchart.ehr.support.TestContainers;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * application-dev.yml runs with ddl-auto=validate. Against the Flyway schema that startup fails today,
 * so the app only boots with the default ddl-auto=none. A Hibernate 6 upgrade changes type mapping
 * rules and must re-baseline this.
 */
class SchemaValidationTest {

    @Test
    void hibernateSchemaValidationRejectsFlywaySchema() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(MedchartEhrApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test");
        String[] args = {
                "--spring.datasource.url=" + TestContainers.POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + TestContainers.POSTGRES.getUsername(),
                "--spring.datasource.password=" + TestContainers.POSTGRES.getPassword(),
                "--spring.redis.host=" + TestContainers.REDIS.getHost(),
                "--spring.redis.port=" + TestContainers.REDIS.getMappedPort(6379),
                "--spring.jpa.hibernate.ddl-auto=validate"};

        assertThatThrownBy(() -> app.run(args))
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .hasRootCauseMessage("Schema-validation: wrong column type encountered in column [resource_id] "
                        + "in table [audit_events]; found [varchar (Types#VARCHAR)], but expecting [int8 (Types#BIGINT)]");
    }
}
