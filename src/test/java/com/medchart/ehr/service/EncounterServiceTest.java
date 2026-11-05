package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    private EncounterRepository repo;

    private EncounterService service;

    @BeforeEach
    void setUp() {
        service = new EncounterService(repo);
    }

    private static Encounter encounter() {
        Encounter e = new Encounter();
        e.setId(1L);
        e.setEncounterNumber("ENC-2026-000001");
        e.setStatus(EncounterStatus.SCHEDULED);
        Patient p = new Patient();
        p.setMrn("MRN-T1");
        e.setPatient(p);
        return e;
    }

    @Test
    @DisplayName("BR-SVC-18: new encounters get number ENC-<current year>-<6-digit sequence> and status SCHEDULED")
    void brSvc18_createAssignsNumberAndStatus() {
        when(repo.save(any(Encounter.class))).thenAnswer(i -> i.getArgument(0));
        Encounter first = encounter();
        first.setStatus(EncounterStatus.COMPLETED);
        Encounter second = encounter();

        service.create(first);
        service.create(second);

        String year = String.valueOf(LocalDate.now().getYear());
        assertEquals("ENC-" + year + "-000101", first.getEncounterNumber());
        assertEquals("ENC-" + year + "-000102", second.getEncounterNumber());
        assertEquals(EncounterStatus.SCHEDULED, first.getStatus());
        assertTrue(second.getEncounterNumber().matches("ENC-\\d{4}-\\d{6}"));
    }

    private void assertTransition(Consumer<Long> action, EncounterStatus expected) {
        Encounter e = encounter();
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        action.accept(1L);
        assertEquals(expected, e.getStatus());
        verify(repo).save(e);
    }

    @Test
    @DisplayName("BR-SVC-19: check-in moves an encounter to CHECKED_IN")
    void brSvc19_checkIn() {
        assertTransition(service::checkIn, EncounterStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("BR-SVC-19: starting moves an encounter to IN_PROGRESS")
    void brSvc19_start() {
        assertTransition(service::startEncounter, EncounterStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("BR-SVC-19: cancelling moves an encounter to CANCELLED")
    void brSvc19_cancel() {
        assertTransition(service::cancelEncounter, EncounterStatus.CANCELLED);
    }

    @Test
    @DisplayName("BR-SVC-19: no-show moves an encounter to NO_SHOW")
    void brSvc19_noShow() {
        assertTransition(service::markNoShow, EncounterStatus.NO_SHOW);
    }

    @Test
    @DisplayName("BR-SVC-19: completing sets COMPLETED and records notes only when provided")
    void brSvc19_completeNotes() {
        Encounter e = encounter();
        e.setNotes("original");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        service.completeEncounter(1L, null);
        assertEquals(EncounterStatus.COMPLETED, e.getStatus());
        assertEquals("original", e.getNotes());

        service.completeEncounter(1L, "Discharged home");
        assertEquals("Discharged home", e.getNotes());
    }

    @Test
    @DisplayName("BR-SVC-19: status transitions on unknown encounters are no-ops")
    void brSvc19_unknownEncounterNoOp() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        service.checkIn(99L);
        service.startEncounter(99L);
        service.completeEncounter(99L, "x");
        service.cancelEncounter(99L);
        service.markNoShow(99L);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("BR-SVC-20: a provider's daily schedule spans [start of day, start of next day)")
    void brSvc20_scheduleDayWindow() {
        LocalDate d = LocalDate.of(2026, 2, 28);
        List<Encounter> list = List.of(encounter());
        when(repo.findTodaysSchedule(4L, d.atStartOfDay(), LocalDate.of(2026, 3, 1).atStartOfDay())).thenReturn(list);
        assertSame(list, service.getProviderSchedule(4L, d));
    }

    @Test
    @DisplayName("BR-SVC-20: date-range search includes the whole end date (exclusive next-day bound)")
    void brSvc20_dateRangeInclusiveEnd() {
        LocalDate start = LocalDate.of(2026, 12, 30);
        LocalDate end = LocalDate.of(2026, 12, 31);
        List<Encounter> list = List.of(encounter());
        when(repo.findByDateRange(start.atStartOfDay(), LocalDateTime.of(2027, 1, 1, 0, 0))).thenReturn(list);
        assertSame(list, service.findByDateRange(start, end));
    }

    @Test
    @DisplayName("BR-SVC-23: encounter lookups and updates delegate to the repository unchanged")
    void brSvc23_encounterDelegation() {
        Encounter e = encounter();
        Pageable pg = PageRequest.of(0, 5);
        Page<Encounter> page = Page.empty(pg);
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(repo.findByIdWithDetails(1L)).thenReturn(Optional.of(e));
        when(repo.findByEncounterNumber("ENC-1")).thenReturn(Optional.of(e));
        when(repo.findByPatientId(2L)).thenReturn(List.of(e));
        when(repo.findByPatientId(2L, pg)).thenReturn(page);
        when(repo.findByAttendingProviderId(3L)).thenReturn(List.of(e));
        when(repo.findByStatus(EncounterStatus.SCHEDULED)).thenReturn(List.of(e));
        when(repo.countByPatientId(2L)).thenReturn(4L);
        when(repo.save(e)).thenReturn(e);

        assertSame(e, service.findById(1L).get());
        assertSame(e, service.findByIdWithDetails(1L).get());
        assertSame(e, service.findByIdForProvider(1L, 3L).get());
        assertSame(e, service.findByEncounterNumber("ENC-1").get());
        assertEquals(1, service.findByPatientId(2L).size());
        assertSame(page, service.findByPatientId(2L, pg));
        assertEquals(1, service.findByProviderId(3L).size());
        assertEquals(1, service.findByStatus(EncounterStatus.SCHEDULED).size());
        assertEquals(4L, service.getPatientEncounterCount(2L));
        assertSame(e, service.update(e));
    }

    @Test
    @DisplayName("BR-SVC-23: provider access to a missing encounter returns empty")
    void brSvc23_providerAccessMissing() {
        when(repo.findByIdWithDetails(5L)).thenReturn(Optional.empty());
        assertTrue(service.findByIdForProvider(5L, 3L).isEmpty());
    }
}
