package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.BlockType;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderAvailability;
import com.medchart.ehr.dto.ProviderAvailabilityDTO;
import com.medchart.ehr.repository.ProviderAvailabilityRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderAvailabilityServiceTest {

    @Mock
    private ProviderAvailabilityRepository availabilityRepository;

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderAvailabilityService service;

    private Provider provider;

    @BeforeEach
    void setUp() {
        provider = new Provider();
        provider.setId(1L);
        provider.setFirstName("Sarah");
        provider.setLastName("Anderson");
    }

    @Test
    void create_withValidRecurringBlock_shouldSucceed() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDuration(30)
                .recurring(true)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.save(any(ProviderAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProviderAvailability result = service.create(1L, dto);

        assertNotNull(result);
        assertEquals(BlockType.AVAILABLE, result.getBlockType());
        assertEquals(1, result.getDayOfWeek());
        assertTrue(result.getRecurring());
        verify(availabilityRepository).save(any(ProviderAvailability.class));
    }

    @Test
    void create_withValidSpecificDateBlock_shouldSucceed() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.LUNCH)
                .specificDate(LocalDate.of(2024, 3, 15))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .slotDuration(30)
                .recurring(false)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.save(any(ProviderAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProviderAvailability result = service.create(1L, dto);

        assertNotNull(result);
        assertEquals(BlockType.LUNCH, result.getBlockType());
        assertFalse(result.getRecurring());
        assertEquals(LocalDate.of(2024, 3, 15), result.getSpecificDate());
    }

    @Test
    void create_withInvalidProvider_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(999L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .recurring(true)
                .build();

        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(999L, dto));
    }

    @Test
    void validate_startTimeAfterEndTime_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(8, 0))
                .recurring(true)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(1L, dto));
        assertTrue(ex.getMessage().contains("Start time must be before end time"));
    }

    @Test
    void validate_recurringWithoutDayOfWeek_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .recurring(true)
                .dayOfWeek(null)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(1L, dto));
        assertTrue(ex.getMessage().contains("Day of week is required"));
    }

    @Test
    void validate_nonRecurringWithoutSpecificDate_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.MEETING)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .recurring(false)
                .specificDate(null)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(1L, dto));
        assertTrue(ex.getMessage().contains("Specific date is required"));
    }

    @Test
    void validate_invalidSlotDuration_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDuration(20)
                .recurring(true)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(1L, dto));
        assertTrue(ex.getMessage().contains("Slot duration must be"));
    }

    @Test
    void validate_invalidDayOfWeek_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(7)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .recurring(true)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(1L, dto));
        assertTrue(ex.getMessage().contains("Day of week must be between"));
    }

    @Test
    void deactivate_shouldSetActiveToFalse() {
        ProviderAvailability existing = ProviderAvailability.builder()
                .id(1L)
                .provider(provider)
                .blockType(BlockType.AVAILABLE)
                .active(true)
                .build();

        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(ProviderAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate(1L);

        assertFalse(existing.getActive());
        verify(availabilityRepository).save(existing);
    }

    @Test
    void findEffectiveForDate_shouldCombineRecurringAndOverrides() {
        LocalDate date = LocalDate.of(2024, 3, 18); // Monday = dayOfWeek 1

        ProviderAvailability recurringBlock = ProviderAvailability.builder()
                .id(1L)
                .blockType(BlockType.AVAILABLE)
                .recurring(true)
                .dayOfWeek(1)
                .build();

        ProviderAvailability overrideBlock = ProviderAvailability.builder()
                .id(2L)
                .blockType(BlockType.MEETING)
                .recurring(false)
                .specificDate(date)
                .build();

        when(availabilityRepository.findEffectiveRecurringForDate(eq(1L), anyInt(), eq(date)))
                .thenReturn(new java.util.ArrayList<>(Collections.singletonList(recurringBlock)));
        when(availabilityRepository.findByProviderIdAndSpecificDateAndActiveTrue(1L, date))
                .thenReturn(Collections.singletonList(overrideBlock));

        List<ProviderAvailability> result = service.findEffectiveForDate(1L, date);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getBlockType() == BlockType.AVAILABLE));
        assertTrue(result.stream().anyMatch(a -> a.getBlockType() == BlockType.MEETING));
    }

    @Test
    void update_shouldModifyExistingBlock() {
        ProviderAvailability existing = ProviderAvailability.builder()
                .id(1L)
                .provider(provider)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDuration(30)
                .recurring(true)
                .active(true)
                .build();

        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.ADMIN)
                .dayOfWeek(1)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .slotDuration(30)
                .recurring(true)
                .build();

        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(ProviderAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProviderAvailability result = service.update(1L, dto);

        assertEquals(BlockType.ADMIN, result.getBlockType());
        assertEquals(LocalTime.of(13, 0), result.getStartTime());
        assertEquals(LocalTime.of(14, 0), result.getEndTime());
    }

    @Test
    void update_withInvalidId_shouldThrow() {
        ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                .providerId(1L)
                .blockType(BlockType.AVAILABLE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .recurring(true)
                .build();

        when(availabilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.update(999L, dto));
    }

    @Test
    void findByProvider_shouldDelegateToRepository() {
        when(availabilityRepository.findByProviderIdAndActiveTrue(1L))
                .thenReturn(Collections.emptyList());

        List<ProviderAvailability> result = service.findByProvider(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(availabilityRepository).findByProviderIdAndActiveTrue(1L);
    }

    @Test
    void create_withAllBlockTypes_shouldSucceed() {
        for (BlockType type : BlockType.values()) {
            ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                    .providerId(1L)
                    .blockType(type)
                    .dayOfWeek(1)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(12, 0))
                    .slotDuration(30)
                    .recurring(true)
                    .build();

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(availabilityRepository.save(any(ProviderAvailability.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProviderAvailability result = service.create(1L, dto);
            assertEquals(type, result.getBlockType());
        }
    }

    @Test
    void validate_allValidSlotDurations_shouldSucceed() {
        int[] validDurations = {15, 30, 45, 60};
        for (int duration : validDurations) {
            ProviderAvailabilityDTO dto = ProviderAvailabilityDTO.builder()
                    .providerId(1L)
                    .blockType(BlockType.AVAILABLE)
                    .dayOfWeek(1)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(12, 0))
                    .slotDuration(duration)
                    .recurring(true)
                    .build();

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(availabilityRepository.save(any(ProviderAvailability.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProviderAvailability result = service.create(1L, dto);
            assertEquals(duration, result.getSlotDuration());
        }
    }
}
