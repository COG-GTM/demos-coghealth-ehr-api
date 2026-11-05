package com.medchart.ehr.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterExportServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query patientQuery;
    @Mock
    private Query encounterQuery;
    @InjectMocks
    private EncounterExportService service;

    @Test
    @DisplayName("BR-LEG-07: date-range CSV export covers the whole end day (exclusive next-day bound) without SSN")
    void brLeg07_dateRangeCsv() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("BETWEEN ?1 AND ?2")))).thenReturn(encounterQuery);
        Object[] row = {1L, "ENC-2026-000001", "OUTPATIENT", "COMPLETED", "2026-01-31T10:00", "MRN-1", "Test", "Patient", "2000-01-01"};
        when(encounterQuery.getResultList()).thenReturn(List.<Object[]>of(row));

        String csv = new String(service.exportEncountersForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                StandardCharsets.UTF_8);

        verify(encounterQuery).setParameter(1, LocalDate.of(2026, 1, 1).atStartOfDay());
        verify(encounterQuery).setParameter(2, LocalDate.of(2026, 2, 1).atStartOfDay());
        String[] lines = csv.split("\n");
        assertEquals("EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status", lines[0]);
        assertEquals("1,ENC-2026-000001,MRN-1,Test Patient,2000-01-01,2026-01-31T10:00,OUTPATIENT,COMPLETED", lines[1]);
        assertFalse(csv.toUpperCase().contains("SSN"));
    }

    @Test
    @DisplayName("BR-LEG-07: an empty date range exports only the header")
    void brLeg07_emptyRange() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("BETWEEN")))).thenReturn(encounterQuery);
        when(encounterQuery.getResultList()).thenReturn(List.of());
        String csv = new String(service.exportEncountersForDateRange(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 28)),
                StandardCharsets.UTF_8);
        assertEquals(1, csv.split("\n").length);
        verify(encounterQuery).setParameter(2, LocalDate.of(2026, 3, 1).atStartOfDay());
    }

    @Test
    @DisplayName("BR-LEG-08: encounter history shows only the SSN's last four digits, newest encounters first")
    void brLeg08_historyMasksSsn() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.startsWith("SELECT mrn")))).thenReturn(patientQuery);
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("ORDER BY encounter_date_time DESC"))))
                .thenReturn(encounterQuery);
        when(patientQuery.getSingleResult()).thenReturn(new Object[] {"MRN-1", "Test", "Patient", "000-00-1234", "2000-01-01"});
        Object[] enc = {1L, "ENC-2026-000001", 42L, "OUTPATIENT", "COMPLETED", "2026-01-31T10:00"};
        when(encounterQuery.getResultList()).thenReturn(List.<Object[]>of(enc));

        String out = new String(service.exportPatientEncounterHistory(42L), StandardCharsets.UTF_8);

        verify(patientQuery).setParameter(1, 42L);
        verify(encounterQuery).setParameter(1, 42L);
        assertTrue(out.contains("SSN: XXX-XX-1234"));
        assertFalse(out.contains("000-00-1234"));
        assertTrue(out.contains("MRN: MRN-1"));
        assertTrue(out.contains("Encounter #: ENC-2026-000001"));
        assertTrue(out.contains("Type: OUTPATIENT"));
        assertTrue(out.contains("Status: COMPLETED"));
    }

    @Test
    @DisplayName("BR-LEG-08: SSN masking handles missing, short and unformatted values")
    void brLeg08_maskEdgeCases() {
        assertEquals("", EncounterExportService.maskSsn(null));
        assertEquals("XXX-XX-****", EncounterExportService.maskSsn("12"));
        assertEquals("XXX-XX-6789", EncounterExportService.maskSsn("123456789"));
        assertEquals("XXX-XX-0000", EncounterExportService.maskSsn("0000"));
    }

    @TempDir
    Path tmp;

    @Test
    @DisplayName("BR-LEG-22: the all-patients file export contains active patients' demographics and never the SSN")
    void brLeg22_allPatientsFileExport() throws Exception {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("WHERE active = true")
                && !s.contains("ssn")))).thenReturn(encounterQuery);
        Object[] row = {1L, "MRN-1", "Test", "Patient", "2000-01-01", "t@example.test", null, "555-0101",
                "1 Test St", "Testville", "NY", "00000"};
        when(encounterQuery.getResultList()).thenReturn(List.<Object[]>of(row));
        Path file = tmp.resolve("patients.csv");

        service.exportAllPatientsToFile(file.toString());

        List<String> lines = Files.readAllLines(file);
        assertEquals("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip", lines.get(0));
        assertEquals("1,MRN-1,Test,Patient,2000-01-01,t@example.test,null,555-0101,1 Test St,Testville,NY,00000", lines.get(1));
    }

    @Test
    @DisplayName("BR-LEG-22: a file export that cannot be written fails loudly")
    void brLeg22_unwritableExportFails() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("WHERE active = true")))).thenReturn(encounterQuery);
        when(encounterQuery.getResultList()).thenReturn(List.of());
        assertThrows(RuntimeException.class,
                () -> service.exportAllPatientsToFile(tmp.resolve("no/such/dir/x.csv").toString()));
    }
}
