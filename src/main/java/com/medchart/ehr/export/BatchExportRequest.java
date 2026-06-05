package com.medchart.ehr.export;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class BatchExportRequest {

    @NotEmpty(message = "At least one patient ID is required")
    private List<Long> patientIds;

    @NotNull(message = "Export format is required")
    private ExportFormat format;

    @NotNull(message = "Export reason is required")
    private ExportReason reason;

    private String reasonDetails;
}
