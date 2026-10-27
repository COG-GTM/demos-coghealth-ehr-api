package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class HrPtoSyncJob implements CommandLineRunner {

    public static final String JOB_NAME = "hr-pto-sync";
    public static final String OUT_DIR_PROPERTY = "hr.pto.sync.out-dir";
    public static final String DEFAULT_OUT_DIR = "target/interfaces/ie-prod";
    public static final String ERROR_QUEUE_FILE = "error-queue.log";
    /** Marks error-queue lines whose employee id and message fields are percent-encoded; older lines are raw. */
    static final String ERROR_QUEUE_ESCAPED_PREFIX = "v2|";
    public static final String RUNS_FILE = "runs.log";
    public static final String OUTBOUND_DIR = "outbound";
    public static final String RUN_ONCE_ARG = "--hr-pto-sync";

    private static final String SENDING_APP = "COGHEALTH_EHR";
    private static final String SENDING_FACILITY = "HRPTO";
    private static final String RECEIVING_APP = "IEPROD";
    private static final String RECEIVING_FACILITY = "OPTIME";
    private static final DateTimeFormatter HL7_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HL7_DELIMITERS = "|^~\\&";
    private static final Pattern EMPLOYEE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,19}");
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private HrExportReader hrExportReader;

    @Autowired
    private DepartmentMapping departmentMapping;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private int controlIdSeq = 0;

    @Scheduled(cron = "0 0 2 * * *")
    public void nightly() {
        runForDate(hrExportReader.latestExportDate());
    }

    @Override
    public void run(String... args) {
        for (String a : args) {
            if (a != null && a.startsWith(RUN_ONCE_ARG)) {
                String date = null;
                int eq = a.indexOf('=');
                if (eq > 0) {
                    date = a.substring(eq + 1);
                }
                runForDate(hrExportReader.resolveExportDate(date));
            }
        }
    }

    public Map<String, Object> runForDate(String exportDate) {
        LocalDateTime started = LocalDateTime.now();
        log.info("{} start exportDate={}", JOB_NAME, exportDate);
        List<HrPtoRecord> records = hrExportReader.read(exportDate);
        int year = Integer.parseInt(exportDate.substring(0, 4));

        int sent = 0;
        int rejected = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<String>();

        for (HrPtoRecord r : records) {
            Integer dept = departmentMapping.lookup(r.costCenter);
            if (dept == null) {
                String msg = "AE – Unknown department for cost center " + r.costCenter;
                errors.add(msg);
                appendErrorQueue(exportDate, r.employeeId, msg);
                rejected++;
                continue;
            }
            if (!r.hasPto() || !r.isApproved()) {
                skipped++;
                continue;
            }
            String identityError = identityRejection(r);
            if (identityError != null) {
                errors.add(identityError);
                appendErrorQueue(exportDate, r.employeeId, identityError);
                rejected++;
                continue;
            }
            List<LocalDate> days = parsePtoDays(r.ptoStart, r.ptoEnd, year);
            List<OrBlock> blocks = loadOrBlocks(r.employeeId);
            for (LocalDate day : days) {
                for (OrBlock b : blocks) {
                    if (weekdayOf(day).equals(b.weekday)) {
                        String ctl = nextControlId(exportDate);
                        String msg = buildSiuS15(r, dept, b, day, ctl);
                        writeOutbound(exportDate, ctl, msg);
                        sent++;
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("job", JOB_NAME);
        result.put("exportDate", exportDate);
        result.put("startedAt", started.format(LOG_TS));
        result.put("finishedAt", LocalDateTime.now().format(LOG_TS));
        result.put("records", records.size());
        result.put("messagesSent", sent);
        result.put("recordsRejected", rejected);
        result.put("recordsSkipped", skipped);
        result.put("status", rejected > 0 ? "PARTIAL" : "OK");
        appendRun(result);
        log.info("{} done exportDate={} sent={} rejected={} skipped={}", JOB_NAME, exportDate, sent, rejected, skipped);
        return result;
    }

    public static List<LocalDate> parsePtoDays(String ptoStart, String ptoEnd, int year) {
        List<LocalDate> days = new ArrayList<LocalDate>();
        if (ptoStart == null || ptoStart.trim().length() == 0) {
            return days;
        }
        String s = ptoStart.trim();
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate start = LocalDate.parse(s);
            LocalDate end = start;
            if (ptoEnd != null && ptoEnd.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                end = LocalDate.parse(ptoEnd.trim());
            }
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                days.add(d);
            }
            return days;
        }
        String[] tokens = s.replace(",", "").split(" ");
        for (String t : tokens) {
            if (t.indexOf('/') < 0) {
                continue;
            }
            if (t.indexOf('-') > 0) {
                String[] range = t.split("-");
                LocalDate start = mmdd(range[0], year);
                LocalDate end = mmdd(range[1], year);
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    days.add(d);
                }
            } else {
                days.add(mmdd(t, year));
            }
            break;
        }
        return days;
    }

    private static LocalDate mmdd(String t, int year) {
        String[] p = t.split("/");
        return LocalDate.of(year, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    public static String weekdayOf(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow.name().substring(0, 3);
    }

    public static String identityRejection(HrPtoRecord r) {
        if (r.employeeId == null || !EMPLOYEE_ID.matcher(r.employeeId).matches()) {
            return "AR – Invalid employee id on HR export line " + r.lineNumber + " for cost center " + r.costCenter;
        }
        if (!isSafeName(r.lastName) || !isSafeName(r.firstName)) {
            return "AR – HL7 delimiter or control character in provider name on HR export line " + r.lineNumber
                    + " for cost center " + r.costCenter;
        }
        return null;
    }

    private static boolean isSafeName(String name) {
        if (name == null || name.trim().length() == 0) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || HL7_DELIMITERS.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Escapes HL7 v2 delimiters (|^~\&) and replaces control characters so a value stays inside its component. */
    public static String hl7Escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\E\\"); break;
                case '|': out.append("\\F\\"); break;
                case '^': out.append("\\S\\"); break;
                case '~': out.append("\\R\\"); break;
                case '&': out.append("\\T\\"); break;
                default: out.append(Character.isISOControl(c) ? ' ' : c);
            }
        }
        return out.toString();
    }

    /** Lossless encoding for error-queue fields: percent-encodes '%', '|' and control characters. */
    static String queueEncode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' || c == '|' || Character.isISOControl(c)) {
                out.append(String.format("%%%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String queueDecode(String value) {
        if (value == null || value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%' && i + 4 < value.length()) {
                try {
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                    // not an encoded sequence; keep the literal '%'
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static String buildSiuS15(HrPtoRecord r, int deptId, OrBlock b, LocalDate day, String controlId) {
        String ts = LocalDateTime.now().format(HL7_TS);
        String ymd = day.format(DateTimeFormatter.BASIC_ISO_DATE);
        String start = ymd + b.startTime.replace(":", "").substring(0, 4);
        String end = ymd + b.endTime.replace(":", "").substring(0, 4);
        String employeeId = hl7Escape(r.employeeId);
        String lastName = hl7Escape(r.lastName);
        String firstName = hl7Escape(r.firstName);
        String ptoEnd = hl7Escape(r.ptoEnd);
        StringBuilder m = new StringBuilder();
        m.append("MSH|^~\\&|").append(SENDING_APP).append("|").append(SENDING_FACILITY).append("|")
         .append(RECEIVING_APP).append("|").append(RECEIVING_FACILITY).append("|").append(ts)
         .append("||SIU^S15^SIU_S12|").append(controlId).append("|P|2.5\r");
        m.append("SCH|").append(controlId).append("|||||PTO^Provider time off|BLOCK^OR block hold|")
         .append("|").append(b.endTime.compareTo(b.startTime) > 0 ? minutes(b) : "0").append("|MIN|^^")
         .append(minutes(b)).append("^").append(start).append("^").append(end).append("|||||")
         .append(employeeId).append("^").append(lastName).append("^").append(firstName)
         .append("||||||||Cancelled\r");
        m.append("NTE|1||").append(hl7Escape(r.ptoType)).append(" ").append(hl7Escape(r.ptoStart))
         .append(ptoEnd.length() > 0 ? " - " + ptoEnd : "").append("\r");
        m.append("AIS|1||BLOCK^OR block^L|").append(start).append("||").append(minutes(b)).append("|MIN\r");
        m.append("AIL|1||").append(hl7Escape(b.room)).append("^^^").append(deptId).append("|OR^Operating room\r");
        m.append("AIP|1||").append(employeeId).append("^").append(lastName).append("^").append(firstName)
         .append("^^^Dr.");
        if (r.npi != null && r.npi.length() > 0) {
            m.append("^^^^^^^^NPI");
        }
        m.append("|SURGEON^Primary surgeon|").append(start).append("||").append(minutes(b)).append("|MIN\r");
        return m.toString();
    }

    private static int minutes(OrBlock b) {
        String[] s = b.startTime.split(":");
        String[] e = b.endTime.split(":");
        return (Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1])) - (Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]));
    }

    public List<OrBlock> loadOrBlocks(String employeeId) {
        List<OrBlock> blocks = new ArrayList<OrBlock>();
        if (jdbcTemplate == null) {
            log.warn("No datasource, OR blocks not loaded for {}", employeeId);
            return blocks;
        }
        String sql = "SELECT b.provider_id, b.department_id, b.weekday, b.start_time, b.end_time, b.room " +
                     "FROM or_block b JOIN provider_hr_identity i ON i.provider_id = b.provider_id " +
                     "WHERE i.hr_employee_id = ? AND b.active = true";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, employeeId);
        for (Map<String, Object> row : rows) {
            OrBlock b = new OrBlock();
            b.providerId = ((Number) row.get("provider_id")).longValue();
            b.departmentId = ((Number) row.get("department_id")).intValue();
            b.weekday = String.valueOf(row.get("weekday"));
            b.startTime = String.valueOf(row.get("start_time")).substring(0, 5);
            b.endTime = String.valueOf(row.get("end_time")).substring(0, 5);
            b.room = String.valueOf(row.get("room"));
            blocks.add(b);
        }
        return blocks;
    }

    private synchronized String nextControlId(String exportDate) {
        controlIdSeq++;
        return "HRPTO" + exportDate + String.format("%05d", controlIdSeq);
    }

    public static File outDir() {
        return new File(System.getProperty(OUT_DIR_PROPERTY, DEFAULT_OUT_DIR));
    }

    private void writeOutbound(String exportDate, String controlId, String message) {
        File dir = new File(outDir(), OUTBOUND_DIR + File.separator + exportDate);
        dir.mkdirs();
        File f = new File(dir, controlId + ".hl7");
        try (PrintWriter w = new PrintWriter(new FileWriter(f))) {
            w.print(message);
        } catch (IOException e) {
            log.error("Cannot write outbound message " + f, e);
        }
        log.debug("Queued {}", f.getName());
    }

    private void appendErrorQueue(String exportDate, String employeeId, String message) {
        File dir = outDir();
        dir.mkdirs();
        File f = new File(dir, ERROR_QUEUE_FILE);
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
            w.println(ERROR_QUEUE_ESCAPED_PREFIX + LocalDateTime.now().format(LOG_TS) + "|" + exportDate + "|"
                    + queueEncode(employeeId) + "|"
                    + queueEncode(message));
        } catch (IOException e) {
            log.error("Cannot append to error queue " + f, e);
        }
        log.error("{} {} {}", JOB_NAME, queueEncode(employeeId), queueEncode(message));
    }

    private void appendRun(Map<String, Object> result) {
        File dir = outDir();
        dir.mkdirs();
        File f = new File(dir, RUNS_FILE);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : result.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(e.getKey()).append("\":");
            if (e.getValue() instanceof Number) {
                json.append(e.getValue());
            } else {
                json.append("\"").append(e.getValue()).append("\"");
            }
        }
        json.append("}");
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
            w.println(json);
        } catch (IOException e) {
            log.error("Cannot append to run log " + f, e);
        }
    }

    public List<Map<String, Object>> readErrors() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        File f = new File(outDir(), ERROR_QUEUE_FILE);
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                boolean escaped = line.startsWith(ERROR_QUEUE_ESCAPED_PREFIX);
                if (escaped) {
                    line = line.substring(ERROR_QUEUE_ESCAPED_PREFIX.length());
                }
                String[] p = line.split("\\|", 4);
                if (p.length < 4) {
                    continue;
                }
                Map<String, Object> e = new LinkedHashMap<String, Object>();
                e.put("timestamp", p[0]);
                e.put("exportDate", p[1]);
                String message = escaped ? queueDecode(p[3]) : p[3];
                e.put("employeeId", escaped ? queueDecode(p[2]) : p[2]);
                e.put("code", message.startsWith("AE") ? "AE" : "AR");
                e.put("message", message);
                out.add(e);
            }
        } catch (IOException e) {
            log.error("Cannot read error queue " + f, e);
        }
        return out;
    }

    public List<Map<String, Object>> readRuns() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        File f = new File(outDir(), RUNS_FILE);
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                Map<String, Object> run = new LinkedHashMap<String, Object>();
                String body = line.trim();
                if (body.startsWith("{")) {
                    body = body.substring(1, body.length() - 1);
                }
                for (String kv : body.split(",")) {
                    String[] p = kv.split(":", 2);
                    if (p.length < 2) {
                        continue;
                    }
                    String k = p[0].replace("\"", "").trim();
                    String v = p[1].trim();
                    if (v.startsWith("\"")) {
                        run.put(k, v.replace("\"", ""));
                    } else {
                        run.put(k, Integer.parseInt(v));
                    }
                }
                out.add(run);
            }
        } catch (IOException e) {
            log.error("Cannot read run log " + f, e);
        }
        return out;
    }

    public Map<String, Integer> errorCountsByCostCenter() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Map<String, Object> e : readErrors()) {
            String msg = String.valueOf(e.get("message"));
            int i = msg.lastIndexOf(' ');
            String cc = i > 0 ? msg.substring(i + 1) : msg;
            Integer c = counts.get(cc);
            counts.put(cc, c == null ? 1 : c + 1);
        }
        return counts;
    }
}
