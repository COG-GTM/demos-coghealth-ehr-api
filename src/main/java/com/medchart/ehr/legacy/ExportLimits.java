package com.medchart.ehr.legacy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Bounds applied to every bulk PHI export: exports are always paginated and
 * always limited to a narrow date range.
 */
public final class ExportLimits {

    public static final int MAX_PAGE_SIZE = 500;
    public static final long MAX_RANGE_DAYS = 31;

    private ExportLimits() {
    }

    public static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page > Integer.MAX_VALUE / size) {
            throw new IllegalArgumentException("page is too large for the requested size");
        }
    }

    public static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate.atStartOfDay(), endDate.atStartOfDay());
    }

    public static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    public static int offset(int page, int size) {
        return page * size;
    }

    /**
     * Renders an SSN as its last four digits only; full SSNs never leave the system.
     */
    public static String maskSsn(Object ssn) {
        if (ssn == null) {
            return "";
        }
        String digits = ssn.toString().replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + digits.substring(digits.length() - 4);
    }
}
