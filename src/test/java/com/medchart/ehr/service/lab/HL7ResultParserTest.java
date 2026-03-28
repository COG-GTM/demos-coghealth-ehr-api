package com.medchart.ehr.service.lab;

import com.medchart.ehr.dto.ParsedLabResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HL7ResultParserTest {

    private HL7ResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new HL7ResultParser();
    }

    @Test
    void parsesValidOruR01Message() {
        String hl7 = "MSH|^~\\&|LAB|QUEST|EHR|MEDCHART|20240118101500||ORU^R01^ORU_R01|MSG001|P|2.5.1\r"
                + "PID|1||MRN-2019-00001^^^MedChart^MR||Smith^John^Robert||19650315|M\r"
                + "ORC|RE|ORD-001|||CM\r"
                + "OBR|1|ORD-001||GLU^Glucose^L|||20240118081500|||||||20240118101500||||||||||F\r"
                + "OBX|1|NM|GLU^Glucose^L||95|mg/dL|70-100|N|||F|||20240118101500\r";

        List<ParsedLabResult> results = parser.parse(hl7);

        assertEquals(1, results.size());
        ParsedLabResult result = results.get(0);
        assertEquals("MRN-2019-00001", result.getPatientIdentifier());
        assertEquals("John", result.getPatientFirstName());
        assertEquals("Smith", result.getPatientLastName());
        assertEquals("ORD-001", result.getOrderNumber());
        assertEquals("GLU", result.getTestCode());
        assertEquals("FINAL", result.getResultStatus());
    }

    @Test
    void parsesMultipleObservationsInSingleMessage() {
        String hl7 = "MSH|^~\\&|LAB|QUEST|EHR|MEDCHART|20240118101500||ORU^R01^ORU_R01|MSG002|P|2.5.1\r"
                + "PID|1||MRN-2019-00001^^^MedChart^MR||Smith^John||19650315|M\r"
                + "ORC|RE|ORD-001|||CM\r"
                + "OBR|1|ORD-001||BMP^Basic Metabolic Panel^L|||20240118081500|||||||20240118101500||||||||||F\r"
                + "OBX|1|NM|NA^Sodium^L||138|mEq/L|136-145|N|||F|||20240118101500\r"
                + "OBX|2|NM|K^Potassium^L||4.2|mEq/L|3.5-5.0|N|||F|||20240118101500\r"
                + "OBX|3|NM|GLU^Glucose^L||156|mg/dL|70-100|H|||F|||20240118101500\r";

        List<ParsedLabResult> results = parser.parse(hl7);

        assertEquals(3, results.size());
        assertEquals("NA", results.get(0).getTestCode());
        assertEquals("K", results.get(1).getTestCode());
        assertEquals("GLU", results.get(2).getTestCode());
    }

    @Test
    void handlesPreliminaryStatus() {
        String hl7 = "MSH|^~\\&|LAB|QUEST|EHR|MEDCHART|20240118101500||ORU^R01^ORU_R01|MSG003|P|2.5.1\r"
                + "PID|1||MRN-001^^^MedChart^MR||Doe^Jane||19900101|F\r"
                + "ORC|RE|ORD-002|||IP\r"
                + "OBR|1|ORD-002||CBC^Complete Blood Count^L|||20240118081500|||||||20240118091500||||||||||P\r"
                + "OBX|1|NM|WBC^WBC^L||12.4|K/uL|4.5-11.0|H|||P|||20240118091500\r";

        List<ParsedLabResult> results = parser.parse(hl7);

        assertEquals(1, results.size());
        assertEquals("PRELIMINARY", results.get(0).getResultStatus());
    }

    @Test
    void returnsEmptyListForEmptyContent() {
        List<ParsedLabResult> results = parser.parse("");
        assertTrue(results.isEmpty());
    }

    @Test
    void returnsEmptyListForNonHl7Content() {
        List<ParsedLabResult> results = parser.parse("This is not HL7 content");
        assertTrue(results.isEmpty());
    }

    @Test
    void mapsHl7FlagsCorrectly() {
        assertEquals("NORMAL", parser.mapFlag("N"));
        assertEquals("HIGH", parser.mapFlag("H"));
        assertEquals("LOW", parser.mapFlag("L"));
        assertEquals("CRITICAL_HIGH", parser.mapFlag("HH"));
        assertEquals("CRITICAL_LOW", parser.mapFlag("LL"));
        assertEquals("ABNORMAL", parser.mapFlag("A"));
        assertEquals("NORMAL", parser.mapFlag(null));
        assertEquals("NORMAL", parser.mapFlag(""));
    }
}
