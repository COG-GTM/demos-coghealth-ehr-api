package com.medchart.ehr.service.lab;

import com.medchart.ehr.dto.ParsedLabResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@Slf4j
public class CsvResultParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private static final Map<String, Set<String>> REQUIRED_HEADERS = Map.of(
            "test_code", Set.of("test_code", "loinc_code", "code"),
            "result_status", Set.of("result_status", "status")
    );

    public List<ParsedLabResult> parse(String csvContent) {
        List<ParsedLabResult> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty or missing header row");
            }

            String[] headers = parseRow(headerLine);
            Map<String, Integer> headerMap = buildHeaderMap(headers);

            validateRequiredHeaders(headerMap);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    String[] fields = parseRow(line);
                    ParsedLabResult result = mapRowToResult(fields, headerMap);
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line {}: {}", lineNumber, e.getMessage());
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }

        return results;
    }

    public void validate(String csvContent) {
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty or missing header row");
            }

            String[] headers = parseRow(headerLine);
            Map<String, Integer> headerMap = buildHeaderMap(headers);
            validateRequiredHeaders(headerMap);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CSV format: " + e.getMessage(), e);
        }
    }

    private String[] parseRow(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString().trim());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());

        return fields.toArray(new String[0]);
    }

    private Map<String, Integer> buildHeaderMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase().replace(" ", "_"), i);
        }
        return map;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : REQUIRED_HEADERS.entrySet()) {
            boolean found = false;
            for (String alias : entry.getValue()) {
                if (headerMap.containsKey(alias)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(entry.getKey());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV missing required columns: " + String.join(", ", missing));
        }
    }

    private ParsedLabResult mapRowToResult(String[] fields, Map<String, Integer> headerMap) {
        return ParsedLabResult.builder()
                .patientIdentifier(getField(fields, headerMap, "patient_identifier",
                        "patient_id", "patient_mrn", "mrn"))
                .patientFirstName(getField(fields, headerMap, "patient_first_name", "first_name"))
                .patientLastName(getField(fields, headerMap, "patient_last_name", "last_name"))
                .orderNumber(getField(fields, headerMap, "order_number", "order_id", "accession"))
                .testCode(getField(fields, headerMap, "test_code", "loinc_code", "code"))
                .testName(getField(fields, headerMap, "test_name", "test_description", "name"))
                .value(getField(fields, headerMap, "value", "result_value", "result"))
                .numericValue(parseNumericValue(
                        getField(fields, headerMap, "value", "numeric_value", "result_value", "result")))
                .unit(getField(fields, headerMap, "unit", "units"))
                .referenceRange(getField(fields, headerMap, "reference_range", "ref_range", "normal_range"))
                .flag(getField(fields, headerMap, "flag", "abnormal_flag"))
                .resultStatus(getField(fields, headerMap, "result_status", "status"))
                .resultDateTime(parseDateTime(
                        getField(fields, headerMap, "result_date_time", "result_date", "resulted_at")))
                .performingLab(getField(fields, headerMap, "performing_lab", "lab", "source_lab"))
                .build();
    }

    private String getField(String[] fields, Map<String, Integer> headerMap, String... aliases) {
        for (String alias : aliases) {
            Integer idx = headerMap.get(alias);
            if (idx != null && idx < fields.length) {
                String val = fields[idx].trim();
                if (!val.isEmpty()) {
                    return val;
                }
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        log.warn("Could not parse date: {}", value);
        return null;
    }

    private BigDecimal parseNumericValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
