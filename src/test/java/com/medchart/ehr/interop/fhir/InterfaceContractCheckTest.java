package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceContractCheckTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clearReportDir() {
        System.clearProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY);
    }

    @Test
    void wellFormedIdsKeepTheirFileNames() {
        Path resources = tmp.resolve("resources");
        assertEquals(resources.toAbsolutePath().normalize().resolve("Practitioner-E104422.json"),
                InterfaceContractCheck.resourceFile(resources, resource("Practitioner", "E104422")));
        assertEquals(resources.toAbsolutePath().normalize().resolve("Slot-E104422-pto.json"),
                InterfaceContractCheck.resourceFile(resources, resource("Slot", "E104422-pto")));
    }

    @Test
    void traversalIdsResolveInsideTheResourcesDirectory() {
        Path resources = tmp.resolve("resources");
        Path base = resources.toAbsolutePath().normalize();
        for (String id : Arrays.asList("..\\..\\..\\Users\\Public\\x", "../../../../tmp/x", "..", "/etc/passwd",
                "C:\\Windows\\x", "E1\u0000.json", "", null)) {
            Path file = InterfaceContractCheck.resourceFile(resources, resource("Practitioner", id));
            assertEquals(base, file.getParent(), "id " + id);
            String name = file.getFileName().toString();
            assertTrue(name.matches("Practitioner-[A-Za-z0-9_-]*\\.[0-9a-f]{16}\\.json"), name);
        }
    }

    @Test
    void sanitizedIdsDoNotCollide() {
        assertNotEquals(InterfaceContractCheck.safeFileToken("../x"), InterfaceContractCheck.safeFileToken("..\\x"));
        assertNotEquals(InterfaceContractCheck.safeFileToken("a/b"), InterfaceContractCheck.safeFileToken("a_b"));
        String transformed = InterfaceContractCheck.safeFileToken("a/b");
        assertNotEquals(transformed, InterfaceContractCheck.safeFileToken(transformed));
        assertNotEquals(transformed, InterfaceContractCheck.safeFileToken(transformed.replace('.', '_')));
    }

    @Test
    void symlinkedResourcesDirectoryIsRejected() throws IOException {
        Path reportDir = tmp.resolve("report");
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createDirectories(reportDir);
        Files.createSymbolicLink(reportDir.resolve("resources"), outside);
        System.setProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY, reportDir.toString());

        assertThrows(IllegalStateException.class, () -> check(record("E1")).run("20261008"));
        try (Stream<Path> files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void symlinkedResourceFileIsNotFollowed() throws IOException {
        Path reportDir = tmp.resolve("report");
        Path target = tmp.resolve("victim.json");
        Files.write(target, "original".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(reportDir.resolve("resources"));
        Files.createSymbolicLink(reportDir.resolve("resources").resolve("Practitioner-E1.json"), target);
        System.setProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY, reportDir.toString());

        assertThrows(IllegalStateException.class, () -> check(record("E1")).run("20261008"));
        assertEquals("original", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }

    @Test
    void runWritesHostileHrIdsInsideReportDirectory() throws IOException {
        Path reportDir = tmp.resolve("report");
        System.setProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY, reportDir.toString());
        check(record("..\\..\\..\\escaped-win"), record("../../../escaped-posix")).run("20261008");

        try (Stream<Path> all = Files.walk(tmp)) {
            List<Path> json = all.filter(p -> p.toString().endsWith(".json")).collect(Collectors.toList());
            assertTrue(json.size() > 2, json.toString());
            Path base = reportDir.toAbsolutePath().normalize();
            for (Path p : json) {
                assertTrue(p.toAbsolutePath().normalize().startsWith(base), p.toString());
                assertTrue(!p.getFileName().toString().contains(".."), p.toString());
                assertTrue(!Files.isSymbolicLink(p), p.toString());
            }
        }
    }

    private static InterfaceContractCheck check(HrPtoRecord... records) {
        HrExportReader reader = new HrExportReader() {
            @Override
            public List<HrPtoRecord> read(String exportDate) {
                return Arrays.asList(records);
            }
        };
        return new InterfaceContractCheck(reader, new DepartmentMapping(), new FhirR4ExportService(),
                new FhirR4ContractValidator());
    }

    private static ExportedResource resource(String type, String id) {
        return new ExportedResource(type, id, id, "{}");
    }

    private static HrPtoRecord record(String employeeId) {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = employeeId;
        r.lastName = "Mallory";
        r.firstName = "Eve";
        r.npi = "";
        r.costCenter = "CC-230-5410";
        r.ptoStart = "10/13-10/17 vacation";
        r.ptoEnd = "";
        r.ptoType = "Vacation";
        r.status = "Approved";
        r.notes = "";
        return r;
    }
}
