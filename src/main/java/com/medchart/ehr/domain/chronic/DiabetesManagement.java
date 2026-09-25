package com.medchart.ehr.domain.chronic;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Diabetes-specific management tracking.
 * 
 * Tracks HbA1c levels, glucose monitoring, and diabetes-specific
 * quality measures for the Chronic Medication Module.
 */
@Entity
@Table(name = "diabetes_management")
@Data
@NoArgsConstructor
public class DiabetesManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "chronic_condition_id", nullable = false)
    private Long chronicConditionId;

    // HbA1c Tracking
    @Column(name = "last_hba1c_value", precision = 4, scale = 1)
    private BigDecimal lastHba1cValue;

    @Column(name = "last_hba1c_date")
    private LocalDate lastHba1cDate;

    @Column(name = "target_hba1c", precision = 4, scale = 1)
    private BigDecimal targetHba1c;

    @Enumerated(EnumType.STRING)
    @Column(name = "hba1c_control_status")
    private ControlStatus hba1cControlStatus;

    // Blood Glucose Monitoring
    @Column(name = "uses_cgm")
    private boolean usesCgm; // Continuous Glucose Monitor

    @Column(name = "cgm_device_type")
    private String cgmDeviceType;

    @Column(name = "avg_daily_glucose", precision = 5, scale = 1)
    private BigDecimal avgDailyGlucose;

    @Column(name = "time_in_range_percent", precision = 5, scale = 2)
    private BigDecimal timeInRangePercent;

    // Insulin Management
    @Column(name = "on_insulin")
    private boolean onInsulin;

    @Column(name = "insulin_regimen")
    private String insulinRegimen; // Basal, Bolus, Mixed, Pump

    @Column(name = "uses_insulin_pump")
    private boolean usesInsulinPump;

    @Column(name = "pump_type")
    private String pumpType;

    // Complications Screening
    @Column(name = "last_eye_exam_date")
    private LocalDate lastEyeExamDate;

    @Column(name = "last_foot_exam_date")
    private LocalDate lastFootExamDate;

    @Column(name = "last_nephropathy_screen_date")
    private LocalDate lastNephropathyScreenDate;

    @Column(name = "has_retinopathy")
    private Boolean hasRetinopathy;

    @Column(name = "has_neuropathy")
    private Boolean hasNeuropathy;

    @Column(name = "has_nephropathy")
    private Boolean hasNephropathy;

    // Quality Measures
    @Column(name = "statin_prescribed")
    private boolean statinPrescribed;

    @Column(name = "ace_arb_prescribed")
    private boolean aceArbPrescribed;

    @Column(name = "last_lipid_panel_date")
    private LocalDate lastLipidPanelDate;

    @Column(name = "last_bp_reading")
    private String lastBpReading;

    @Column(name = "last_bp_date")
    private LocalDate lastBpDate;

    // Education & Self-Management
    @Column(name = "completed_dsme")
    private boolean completedDsme; // Diabetes Self-Management Education

    @Column(name = "dsme_completion_date")
    private LocalDate dsmeCompletionDate;

    @Column(name = "has_nutritionist")
    private boolean hasNutritionist;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateControlStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateControlStatus();
    }

    private void calculateControlStatus() {
        if (lastHba1cValue == null) {
            hba1cControlStatus = ControlStatus.UNKNOWN;
        } else if (lastHba1cValue.compareTo(new BigDecimal("7.0")) <= 0) {
            hba1cControlStatus = ControlStatus.CONTROLLED;
        } else if (lastHba1cValue.compareTo(new BigDecimal("9.0")) <= 0) {
            hba1cControlStatus = ControlStatus.SUBOPTIMAL;
        } else {
            hba1cControlStatus = ControlStatus.UNCONTROLLED;
        }
    }

    public enum ControlStatus {
        CONTROLLED,     // HbA1c <= 7.0%
        SUBOPTIMAL,     // HbA1c 7.1-9.0%
        UNCONTROLLED,   // HbA1c > 9.0%
        UNKNOWN
    }

    /**
     * Check if annual eye exam is overdue.
     */
    public boolean isEyeExamOverdue() {
        if (lastEyeExamDate == null) return true;
        return lastEyeExamDate.plusYears(1).isBefore(LocalDate.now());
    }

    /**
     * Check if foot exam is overdue (should be done at every visit).
     */
    public boolean isFootExamOverdue() {
        if (lastFootExamDate == null) return true;
        return lastFootExamDate.plusMonths(3).isBefore(LocalDate.now());
    }

    /**
     * Check if nephropathy screening is overdue (annual).
     */
    public boolean isNephropathyScreenOverdue() {
        if (lastNephropathyScreenDate == null) return true;
        return lastNephropathyScreenDate.plusYears(1).isBefore(LocalDate.now());
    }
}
