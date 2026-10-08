package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.legacy.HrPtoRecord;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the PTO period of an HR export row to America/New_York instants.
 *
 * Source precedence: the pto_start / pto_end columns (ISO yyyy-MM-dd, or free text such as
 * "10/13-10/17 vacation" / "Thu 10/22, half day AM" when Workday leaves text in the column);
 * the free-text notes are parsed only when both columns are empty.
 */
public final class PtoPeriodParser {

    public static final ZoneId ZONE = ZoneId.of("America/New_York");
    public static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?:\\s*-\\s*(\\d{1,2})/(\\d{1,2}))?(?![\\d/])");
    /** Workflow timestamps in notes ("approved by dept admin 09/22") are not leave dates. */
    private static final Pattern AUDIT_DATE = Pattern.compile(
            "\\b(approved|submitted|requested|entered|updated|reviewed|cancell?ed|denied)\\b[^,;]*?\\d{1,2}/\\d{1,2}(/\\d{2,4})?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HALF_DAY = Pattern.compile("half[\\s-]*day\\s*(AM|PM)?", Pattern.CASE_INSENSITIVE);
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final LocalTime NOON = LocalTime.NOON;

    private PtoPeriodParser() {
    }

    public enum Source { COLUMNS, NOTES }

    public static final class PtoPeriod {
        public final ZonedDateTime start;
        public final ZonedDateTime end;
        public final Source source;

        PtoPeriod(ZonedDateTime start, ZonedDateTime end, Source source) {
            this.start = start;
            this.end = end;
            this.source = source;
        }

        public String startInstant() {
            return start.format(INSTANT);
        }

        public String endInstant() {
            return end.format(INSTANT);
        }
    }

    /** @param exportDay HR export date, used to infer the year of month/day free text; null = today */
    public static PtoPeriod parse(HrPtoRecord r, LocalDate exportDay) {
        LocalDate ref = exportDay != null ? exportDay : LocalDate.now(ZONE);
        String start = trim(r.ptoStart);
        String end = trim(r.ptoEnd);
        if (start.length() > 0 || end.length() > 0) {
            if (ISO_DATE.matcher(start).matches()) {
                LocalDate s = LocalDate.parse(start);
                LocalDate e = ISO_DATE.matcher(end).matches() ? LocalDate.parse(end) : s;
                if (e.isBefore(s)) {
                    return null;
                }
                return new PtoPeriod(s.atStartOfDay(ZONE), e.atTime(END_OF_DAY).atZone(ZONE), Source.COLUMNS);
            }
            return parseFreeText((start + " " + end).trim(), ref, Source.COLUMNS);
        }
        return parseFreeText(AUDIT_DATE.matcher(trim(r.notes)).replaceAll(""), ref, Source.NOTES);
    }

    static PtoPeriod parseFreeText(String text, LocalDate ref, Source source) {
        if (text == null || text.length() == 0) {
            return null;
        }
        Matcher m = MONTH_DAY.matcher(text);
        if (!m.find()) {
            return null;
        }
        LocalDate s = monthDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), ref);
        if (s == null) {
            return null;
        }
        LocalDate e = s;
        if (m.group(3) != null) {
            e = monthDay(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), s);
            if (e == null) {
                return null;
            }
        }
        LocalTime from = LocalTime.MIDNIGHT;
        LocalTime to = END_OF_DAY;
        Matcher half = HALF_DAY.matcher(text);
        if (s.equals(e) && half.find() && half.group(1) != null) {
            if ("AM".equals(half.group(1).toUpperCase(Locale.ROOT))) {
                to = NOON;
            } else {
                from = NOON;
            }
        }
        return new PtoPeriod(s.atTime(from).atZone(ZONE), e.atTime(to).atZone(ZONE), source);
    }

    /** Month/day without a year: the first occurrence on or after ref minus 6 months. */
    private static LocalDate monthDay(int month, int day, LocalDate ref) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        try {
            LocalDate d = LocalDate.of(ref.getYear(), month, day);
            if (d.isBefore(ref.minusMonths(6))) {
                d = d.plusYears(1);
            }
            return d;
        } catch (java.time.DateTimeException ex) {
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
