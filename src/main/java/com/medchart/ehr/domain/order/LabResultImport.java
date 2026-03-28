package com.medchart.ehr.domain.order;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_result_imports", indexes = {
    @Index(name = "idx_import_status", columnList = "status"),
    @Index(name = "idx_import_date", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResultImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 10)
    private String fileType;

    private Long fileSizeBytes;

    @Column(length = 100)
    private String sourceLab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalResults = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer unmatchedCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 100)
    private String importedBy;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public double getMatchRate() {
        if (totalResults == 0) return 0.0;
        return (double) matchedCount / totalResults * 100.0;
    }
}
