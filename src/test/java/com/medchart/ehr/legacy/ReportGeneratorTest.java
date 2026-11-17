package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ReportGenerator reportGenerator;

    @Test
    void departmentProductivityReportUsesConstantQueryCount() {
        Query departmentQuery = mock(Query.class);
        when(departmentQuery.getResultList()).thenReturn(Arrays.asList("Surgery", "Cardiology", "Empty"));
        Query providerQuery = mock(Query.class);
        when(providerQuery.getResultList()).thenReturn(Arrays.asList(
                new Object[]{"Cardiology", "Rodriguez", BigInteger.valueOf(4)},
                new Object[]{"Surgery", "Okafor", BigInteger.ZERO},
                new Object[]{"Surgery", "Thompson", BigInteger.valueOf(2)}));
        when(entityManager.createNativeQuery(startsWith("SELECT DISTINCT department"))).thenReturn(departmentQuery);
        when(entityManager.createNativeQuery(startsWith("SELECT pr.department"))).thenReturn(providerQuery);

        String report = reportGenerator.generateDepartmentProductivityReport();

        String nl = System.lineSeparator();
        assertThat(report.substring(report.indexOf(nl + nl) + 2 * nl.length())).isEqualTo(
                "Department: Surgery" + nl
                        + "  Okafor: 0 encounters" + nl
                        + "  Thompson: 2 encounters" + nl + nl
                        + "Department: Cardiology" + nl
                        + "  Rodriguez: 4 encounters" + nl + nl
                        + "Department: Empty" + nl + nl);
        verify(entityManager, times(2)).createNativeQuery(anyString());
    }

    @Test
    void departmentProductivityReportWithNoDepartments() {
        Query emptyQuery = mock(Query.class);
        when(emptyQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString())).thenReturn(emptyQuery);

        String report = reportGenerator.generateDepartmentProductivityReport();

        assertThat(report).startsWith("DEPARTMENT PRODUCTIVITY REPORT");
        assertThat(report).doesNotContain("Department:");
    }
}
