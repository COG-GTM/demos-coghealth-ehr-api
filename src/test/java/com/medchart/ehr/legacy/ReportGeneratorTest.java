package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private ReportGenerator reportGenerator;

    @Test
    void departmentProductivityUsesOneConstantQueryForDepartmentNames() {
        String department = "' UNION SELECT 'x',1--";
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{department, "Literal Provider", 0L}));

        String report = reportGenerator.generateDepartmentProductivityReport();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(1)).createNativeQuery(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("'"));
        assertFalse(sql.contains("UNION"));
        assertTrue(report.contains("Department: " + department));
    }
}
