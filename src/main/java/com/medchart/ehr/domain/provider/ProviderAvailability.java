package com.medchart.ehr.domain.provider;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "provider_availability", indexes = {
    @Index(name = "idx_provider_avail_provider", columnList = "provider_id"),
    @Index(name = "idx_provider_avail_day", columnList = "dayOfWeek"),
    @Index(name = "idx_provider_avail_date", columnList = "specificDate"),
    @Index(name = "idx_provider_avail_block_type", columnList = "blockType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 30)
    private BlockType blockType;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration", nullable = false)
    @Builder.Default
    private Integer slotDuration = 30;

    @Column(name = "visit_types_allowed", length = 500)
    private String visitTypesAllowed;

    @Column(nullable = false)
    @Builder.Default
    private Boolean recurring = false;

    @Column(name = "recurrence_pattern", length = 50)
    private String recurrencePattern;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "override_reason", length = 200)
    private String overrideReason;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;
}
