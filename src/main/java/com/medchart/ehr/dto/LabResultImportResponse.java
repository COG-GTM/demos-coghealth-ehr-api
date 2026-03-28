package com.medchart.ehr.dto;

import com.medchart.ehr.domain.order.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResultImportResponse {

    private Long importId;
    private String fileName;
    private String fileType;
    private ImportStatus status;
    private Integer totalResults;
    private Integer matchedCount;
    private Integer unmatchedCount;
    private Integer errorCount;
    private String errorMessage;
    private Double matchRate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
