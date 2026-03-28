package com.medchart.ehr.dto;

import com.medchart.ehr.domain.provider.BlockType;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAvailabilityDTO {

    private Long id;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Block type is required")
    private BlockType blockType;

    private Integer dayOfWeek;

    private LocalDate specificDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @Builder.Default
    private Integer slotDuration = 30;

    private String visitTypesAllowed;

    @Builder.Default
    private Boolean recurring = false;

    private String recurrencePattern;

    private LocalDate effectiveFrom;

    private LocalDate effectiveUntil;

    private String overrideReason;

    private String notes;

    @Builder.Default
    private Boolean active = true;
}
