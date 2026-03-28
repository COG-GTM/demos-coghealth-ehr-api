package com.medchart.ehr.service.lab;

import com.medchart.ehr.dto.ParsedLabResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvResultParserTest {

    private CsvResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvResultParser();
    }

    @Test
    void parsesValidCsvWithAllColumns() {
        String csv = "patient_identifier,order_number,test_code,test_name,value,unit,reference_range,flag,result_status,result_date_time,performing_lab\n"
                + "MRN-2019-00001,ORD-001,GLU,Glucose,95,mg/dL,70-100,NORMAL,FINAL,2024-01-18 10:15:00,Quest Diagnostics\n"
                + "MRN-2019-00002,ORD-002,HBA1C,Hemoglobin A1c,7.2,%,<5.7,HIGH,FINAL,2024-01-18 10:30:00,LabCorp\n";

        List<ParsedLabResult> results = parser.parse(csv);

        assertEquals(2, results.size());

        ParsedLabResult first = results.get(0);
        assertEquals("MRN-2019-00001", first.getPatientIdentifier());
        assertEquals("ORD-001", first.getOrderNumber());
        assertEquals("GLU", first.getTestCode());
        assertEquals("Glucose", first.getTestName());
        assertEquals("95", first.getValue());
        assertEquals("mg/dL", first.getUnit());
        assertEquals("70-100", first.getReferenceRange());
        assertEquals("NORMAL", first.getFlag());
        assertEquals("FINAL", first.getResultStatus());
        assertEquals("Quest Diagnostics", first.getPerformingLab());
    }

    @Test
    void parsesMinimalCsvWithOnlyRequiredColumns() {
        String csv = "test_code,result_status,value\n"
                + "GLU,FINAL,95\n";

        List<ParsedLabResult> results = parser.parse(csv);

        assertEquals(1, results.size());
        assertEquals("GLU", results.get(0).getTestCode());
        assertEquals("FINAL", results.get(0).getResultStatus());
        assertEquals("95", results.get(0).getValue());
    }

    @Test
    void handlesAlternateColumnNames() {
        String csv = "mrn,accession,code,name,result,units,ref_range,abnormal_flag,status,resulted_at,lab\n"
                + "MRN-001,ACC-001,BUN,Blood Urea Nitrogen,15,mg/dL,7-20,N,FINAL,2024-01-18 10:00:00,Quest\n";

        List<ParsedLabResult> results = parser.parse(csv);

        assertEquals(1, results.size());
        assertEquals("MRN-001", results.get(0).getPatientIdentifier());
        assertEquals("ACC-001", results.get(0).getOrderNumber());
        assertEquals("BUN", results.get(0).getTestCode());
    }

    @Test
    void throwsOnMissingRequiredColumns() {
        String csv = "patient_identifier,value\n"
                + "MRN-001,95\n";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(csv));
        assertTrue(ex.getMessage().contains("test_code"));
    }

    @Test
    void throwsOnEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    @Test
    void skipsBlankLines() {
        String csv = "test_code,result_status,value\n"
                + "\n"
                + "GLU,FINAL,95\n"
                + "\n"
                + "BUN,FINAL,15\n";

        List<ParsedLabResult> results = parser.parse(csv);
        assertEquals(2, results.size());
    }

    @Test
    void handlesQuotedFieldsWithCommas() {
        String csv = "test_code,test_name,result_status,value\n"
                + "GLU,\"Glucose, Fasting\",FINAL,95\n";

        List<ParsedLabResult> results = parser.parse(csv);

        assertEquals(1, results.size());
        assertEquals("Glucose, Fasting", results.get(0).getTestName());
    }

    @Test
    void parsesNumericValues() {
        String csv = "test_code,result_status,value\n"
                + "GLU,FINAL,95.5\n"
                + "COMMENT,FINAL,No abnormality detected\n";

        List<ParsedLabResult> results = parser.parse(csv);

        assertEquals(2, results.size());
        assertNotNull(results.get(0).getNumericValue());
        assertEquals(95.5, results.get(0).getNumericValue().doubleValue(), 0.01);
        assertNull(results.get(1).getNumericValue());
    }

    @Test
    void validatesSuccessfully() {
        String csv = "test_code,result_status\nGLU,FINAL\n";
        assertDoesNotThrow(() -> parser.validate(csv));
    }

    @Test
    void validateFailsOnMissingColumns() {
        String csv = "value,unit\n95,mg/dL\n";
        assertThrows(IllegalArgumentException.class, () -> parser.validate(csv));
    }
}
