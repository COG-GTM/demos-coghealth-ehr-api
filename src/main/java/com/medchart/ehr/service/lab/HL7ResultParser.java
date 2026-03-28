package com.medchart.ehr.service.lab;

import com.medchart.ehr.dto.ParsedLabResult;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v251.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v251.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v251.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v251.message.ORU_R01;
import ca.uhn.hl7v2.model.v251.segment.OBR;
import ca.uhn.hl7v2.model.v251.segment.OBX;
import ca.uhn.hl7v2.model.v251.segment.ORC;
import ca.uhn.hl7v2.model.v251.segment.PID;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class HL7ResultParser {

    private static final DateTimeFormatter HL7_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter HL7_DATETIME_SHORT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public List<ParsedLabResult> parse(String hl7Content) {
        List<ParsedLabResult> results = new ArrayList<>();

        try {
            HapiContext context = new DefaultHapiContext();
            context.setValidationContext(new NoValidation());
            Parser parser = context.getPipeParser();

            // HL7 files may contain multiple messages separated by blank lines
            String[] messageTexts = hl7Content.split("\\r?\\n\\r?\\n");
            for (String messageText : messageTexts) {
                String trimmed = messageText.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("MSH")) {
                    continue;
                }
                // Normalize line endings to \r for HL7 parser
                String normalized = trimmed.replace("\n", "\r");
                try {
                    Message message = parser.parse(normalized);
                    if (message instanceof ORU_R01) {
                        results.addAll(parseOruMessage((ORU_R01) message));
                    } else {
                        log.warn("Skipping non-ORU message type: {}", message.getName());
                    }
                } catch (Exception e) {
                    log.error("Failed to parse HL7 message segment", e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize HL7 parser", e);
            throw new IllegalArgumentException("Invalid HL7 file: " + e.getMessage(), e);
        }

        return results;
    }

    private List<ParsedLabResult> parseOruMessage(ORU_R01 oruMessage) throws Exception {
        List<ParsedLabResult> results = new ArrayList<>();

        int patientResultCount = oruMessage.getPATIENT_RESULTReps();
        for (int i = 0; i < patientResultCount; i++) {
            ORU_R01_PATIENT_RESULT patientResult = oruMessage.getPATIENT_RESULT(i);
            PID pid = patientResult.getPATIENT().getPID();

            String patientId = pid.getPatientIdentifierList(0).getIDNumber().getValue();
            String firstName = pid.getPatientName(0).getGivenName().getValue();
            String lastName = pid.getPatientName(0).getFamilyName().getSurname().getValue();

            int orderObsCount = patientResult.getORDER_OBSERVATIONReps();
            for (int j = 0; j < orderObsCount; j++) {
                ORU_R01_ORDER_OBSERVATION orderObs = patientResult.getORDER_OBSERVATION(j);

                ORC orc = orderObs.getORC();
                String orderNumber = orc.getPlacerOrderNumber().getEntityIdentifier().getValue();

                OBR obr = orderObs.getOBR();
                String performingLab = obr.getFillerField1().getValue();

                int obsCount = orderObs.getOBSERVATIONReps();
                for (int k = 0; k < obsCount; k++) {
                    ORU_R01_OBSERVATION observation = orderObs.getOBSERVATION(k);
                    OBX obx = observation.getOBX();

                    String testCode = obx.getObservationIdentifier().getIdentifier().getValue();
                    String testName = obx.getObservationIdentifier().getText().getValue();
                    String value = extractObxValue(obx);
                    String unit = obx.getUnits().getIdentifier().getValue();
                    String referenceRange = obx.getReferencesRange().getValue();
                    String flag = extractAbnormalFlag(obx);
                    String status = obx.getObservationResultStatus().getValue();
                    LocalDateTime resultDateTime = parseHl7DateTime(
                            obx.getDateTimeOfTheObservation().getTime().getValue());

                    BigDecimal numericValue = parseNumericValue(value);

                    ParsedLabResult result = ParsedLabResult.builder()
                            .patientIdentifier(patientId)
                            .patientFirstName(firstName)
                            .patientLastName(lastName)
                            .orderNumber(orderNumber)
                            .testCode(testCode)
                            .testName(testName)
                            .value(value)
                            .numericValue(numericValue)
                            .unit(unit)
                            .referenceRange(referenceRange)
                            .flag(flag)
                            .resultStatus(mapResultStatus(status))
                            .resultDateTime(resultDateTime)
                            .performingLab(performingLab)
                            .build();

                    results.add(result);
                }
            }
        }

        return results;
    }

    private String extractObxValue(OBX obx) {
        try {
            if (obx.getObservationValue(0) != null && obx.getObservationValue(0).getData() != null) {
                return obx.getObservationValue(0).getData().toString();
            }
        } catch (Exception e) {
            log.debug("Could not extract OBX value", e);
        }
        return null;
    }

    private String extractAbnormalFlag(OBX obx) {
        try {
            if (obx.getAbnormalFlags(0) != null) {
                return obx.getAbnormalFlags(0).getValue();
            }
        } catch (Exception e) {
            log.debug("Could not extract abnormal flag", e);
        }
        return null;
    }

    private LocalDateTime parseHl7DateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            if (dateTimeStr.length() >= 14) {
                return LocalDateTime.parse(dateTimeStr.substring(0, 14), HL7_DATETIME_FORMAT);
            } else if (dateTimeStr.length() >= 12) {
                return LocalDateTime.parse(dateTimeStr.substring(0, 12), HL7_DATETIME_SHORT);
            }
        } catch (DateTimeParseException e) {
            log.warn("Could not parse HL7 datetime: {}", dateTimeStr);
        }
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

    private String mapResultStatus(String hl7Status) {
        if (hl7Status == null) return "FINAL";
        switch (hl7Status.toUpperCase()) {
            case "P": return "PRELIMINARY";
            case "F": return "FINAL";
            case "C": return "CORRECTED";
            case "X": return "CANCELLED";
            case "D": return "CANCELLED";
            default: return "FINAL";
        }
    }

    String mapFlag(String hl7Flag) {
        if (hl7Flag == null || hl7Flag.isEmpty()) return "NORMAL";
        switch (hl7Flag.toUpperCase()) {
            case "N": return "NORMAL";
            case "L": return "LOW";
            case "H": return "HIGH";
            case "LL": return "CRITICAL_LOW";
            case "HH": return "CRITICAL_HIGH";
            case "A": return "ABNORMAL";
            case "POS": return "POSITIVE";
            case "NEG": return "NEGATIVE";
            default: return "ABNORMAL";
        }
    }
}
