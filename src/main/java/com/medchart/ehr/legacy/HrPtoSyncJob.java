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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class HrPtoSyncJob implements CommandLineRunner {

    public static final String JOB_NAME = "hr-pto-sync";
    public static final String OUT_DIR_PROPERTY = "hr.pto.sync.out-dir";
    public static final String DEFAULT_OUT_DIR = "target/interfaces/ie-prod";
    public static final String ERROR_QUEUE_FILE = "error-queue.log";
    public static final String RUNS_FILE = "runs.log";
    public static final String OUTBOUND_DIR = "outbound";
    public static final String SENT_LEDGER_FILE = "sent.idx";
    public static final String RUN_ONCE_ARG = "--hr-pto-sync";

    private static final String SENDING_APP = "COGHEALTH_EHR";
    private static final String SENDING_FACILITY = "HRPTO";
    private static final String RECEIVING_APP = "IEPROD";
    private static final String RECEIVING_FACILITY = "OPTIME";
    private static final DateTimeFormatter HL7_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private HrExportReader hrExportReader;

    @Autowired
    private DepartmentMapping departmentMapping;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private final ReentrantLock runLock = new ReentrantLock();

    @Scheduled(cron = "0 0 2 * * *")
    public void nightly() {
        try {
            runForDate(hrExportReader.latestExportDate());
        } catch (JobAlreadyRunningException e) {
            log.warn("{} nightly run skipped: {}", JOB_NAME, e.getMessage());
        }
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

    /**
     * Runs the job for one export date. Only one run executes at a time; a concurrent call fails fast with
     * {@link JobAlreadyRunningException}. Re-running a date is idempotent: messages already recorded in the
     * per-date sent ledger are not queued again, and identical error-queue entries are not appended twice.
     */
    public Map<String, Object> runForDate(String exportDate) {
        if (!runLock.tryLock()) {
            throw new JobAlreadyRunningException(JOB_NAME + " is already running");
        }
        try {
            return doRun(exportDate);
        } finally {
            runLock.unlock();
        }
    }

    private Map<String, Object> doRun(String exportDate) {
        if (exportDate == null || !exportDate.matches("\\d{8}")) {
            throw new IllegalArgumentException("exportDate must be yyyyMMdd");
        }
        LocalDateTime started = LocalDateTime.now();
        log.info("{} start exportDate={}", JOB_NAME, exportDate);
        List<HrPtoRecord> records = hrExportReader.read(exportDate);
        int year = Integer.parseInt(exportDate.substring(0, 4));

        int sent = 0;
        int rejected = 0;
        int skipped = 0;
        int alreadySent = 0;
        List<String> errors = new ArrayList<String>();
        Map<String, String> ledger = readSentLedger(exportDate);
        int seq = maxControlSeq(exportDate, usedControlIds(exportDate, ledger.values()));
        Set<String> queuedErrors = queuedErrorKeys(exportDate);

        for (HrPtoRecord r : records) {
            Integer dept = departmentMapping.lookup(r.costCenter);
            if (dept == null) {
                String msg = "AE – Unknown department for cost center " + r.costCenter;
                errors.add(msg);
                if (queuedErrors.add(r.employeeId + "|" + msg)) {
                    appendErrorQueue(exportDate, r.employeeId, msg);
                }
                rejected++;
                continue;
            }
            if (!r.hasPto() || !r.isApproved()) {
                skipped++;
                continue;
            }
            List<LocalDate> days = parsePtoDays(r.ptoStart, r.ptoEnd, year);
            List<OrBlock> blocks = loadOrBlocks(r.employeeId);
            for (LocalDate day : days) {
                for (OrBlock b : blocks) {
                    if (weekdayOf(day).equals(b.weekday)) {
                        String ctl = controlId(exportDate, seq + 1);
                        String msg = buildSiuS15(r, dept, b, day, ctl);
                        String key = messageKey(msg);
                        if (ledger.containsKey(key)) {
                            alreadySent++;
                            continue;
                        }
                        seq++;
                        if (writeOutbound(exportDate, ctl, msg)) {
                            appendSentLedger(exportDate, key, ctl);
                            ledger.put(key, ctl);
                            sent++;
                        }
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
        result.put("messagesAlreadySent", alreadySent);
        result.put("recordsRejected", rejected);
        result.put("recordsSkipped", skipped);
        result.put("status", rejected > 0 ? "PARTIAL" : "OK");
        appendRun(result);
        log.info("{} done exportDate={} sent={} alreadySent={} rejected={} skipped={}", JOB_NAME, exportDate, sent,
                alreadySent, rejected, skipped);
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

    public static String buildSiuS15(HrPtoRecord r, int deptId, OrBlock b, LocalDate day, String controlId) {
        String ts = LocalDateTime.now().format(HL7_TS);
        String ymd = day.format(DateTimeFormatter.BASIC_ISO_DATE);
        String start = ymd + b.startTime.replace(":", "").substring(0, 4);
        String end = ymd + b.endTime.replace(":", "").substring(0, 4);
        StringBuilder m = new StringBuilder();
        m.append("MSH|^~\\&|").append(SENDING_APP).append("|").append(SENDING_FACILITY).append("|")
         .append(RECEIVING_APP).append("|").append(RECEIVING_FACILITY).append("|").append(ts)
         .append("||SIU^S15^SIU_S12|").append(controlId).append("|P|2.5\r");
        m.append("SCH|").append(controlId).append("|||||PTO^Provider time off|BLOCK^OR block hold|")
         .append("|").append(b.endTime.compareTo(b.startTime) > 0 ? minutes(b) : "0").append("|MIN|^^")
         .append(minutes(b)).append("^").append(start).append("^").append(end).append("|||||")
         .append(r.employeeId).append("^").append(r.lastName).append("^").append(r.firstName)
         .append("||||||||Cancelled\r");
        m.append("NTE|1||").append(r.ptoType).append(" ").append(r.ptoStart)
         .append(r.ptoEnd.length() > 0 ? " - " + r.ptoEnd : "").append("\r");
        m.append("AIS|1||BLOCK^OR block^L|").append(start).append("||").append(minutes(b)).append("|MIN\r");
        m.append("AIL|1||").append(b.room).append("^^^").append(deptId).append("|OR^Operating room\r");
        m.append("AIP|1||").append(r.employeeId).append("^").append(r.lastName).append("^").append(r.firstName)
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

    static String controlId(String exportDate, int seq) {
        return "HRPTO" + exportDate + String.format("%05d", seq);
    }

    /**
     * Identity of a cancellation, taken from the HL7 payload itself so that messages queued before the sent ledger
     * existed can be recognised: provider (AIP-3.1), location incl. mapped department (AIL-3), block start (AIS-4)
     * and duration (AIS-6). Returns null if the message has none of these segments.
     */
    static String messageKey(String hl7) {
        String provider = null;
        String location = null;
        String start = null;
        String duration = null;
        for (String segment : hl7.split("[\\r\\n]+")) {
            String[] f = segment.split("\\|", -1);
            if (f[0].equals("AIP") && f.length > 3) {
                provider = f[3].split("\\^", -1)[0];
            } else if (f[0].equals("AIL") && f.length > 3) {
                location = f[3];
            } else if (f[0].equals("AIS") && f.length > 6) {
                start = f[4];
                duration = f[6];
            }
        }
        if (provider == null || location == null || start == null) {
            return null;
        }
        return provider + "^" + location + "^" + start + "^" + duration;
    }

    private static int maxControlSeq(String exportDate, Iterable<String> controlIds) {
        String prefix = "HRPTO" + exportDate;
        int max = 0;
        for (String id : controlIds) {
            if (id.startsWith(prefix) && id.length() > prefix.length()) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // not one of ours
                }
            }
        }
        return max;
    }

    private static List<String> usedControlIds(String exportDate, Iterable<String> ledgerIds) {
        List<String> ids = new ArrayList<String>();
        for (String id : ledgerIds) {
            ids.add(id);
        }
        String[] names = outboundDir(exportDate).list();
        if (names != null) {
            for (String n : names) {
                if (n.endsWith(".hl7")) {
                    ids.add(n.substring(0, n.length() - 4));
                }
            }
        }
        return ids;
    }

    private static File outboundDir(String exportDate) {
        return new File(outDir(), OUTBOUND_DIR + File.separator + exportDate);
    }

    private Map<String, String> readSentLedger(String exportDate) {
        Map<String, String> ledger = new LinkedHashMap<String, String>();
        File f = new File(outboundDir(exportDate), SENT_LEDGER_FILE);
        if (f.exists()) {
            readLedgerFile(f, ledger);
        }
        bootstrapFromOutboundFiles(exportDate, ledger);
        return ledger;
    }

    /**
     * Records outbound files whose payload key is not yet in the ledger, e.g. those queued before the ledger existed
     * or recorded under an earlier key format.
     */
    private void bootstrapFromOutboundFiles(String exportDate, Map<String, String> ledger) {
        File[] files = outboundDir(exportDate).listFiles((d, n) -> n.endsWith(".hl7"));
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File hl7 : files) {
            String ctl = hl7.getName().substring(0, hl7.getName().length() - 4);
            String key;
            try {
                key = messageKey(new String(Files.readAllBytes(hl7.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read outbound message " + hl7, e);
            }
            if (key != null && !ledger.containsKey(key)) {
                ledger.put(key, ctl);
                appendSentLedger(exportDate, key, ctl);
            }
        }
    }

    private static void readLedgerFile(File f, Map<String, String> ledger) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                int bar = line.lastIndexOf('|');
                if (bar > 0) {
                    ledger.put(line.substring(0, bar), line.substring(bar + 1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read sent ledger " + f, e);
        }
    }

    private void appendSentLedger(String exportDate, String key, String controlId) {
        File f = new File(outboundDir(exportDate), SENT_LEDGER_FILE);
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
            w.println(key + "|" + controlId);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot append to sent ledger " + f, e);
        }
    }

    private Set<String> queuedErrorKeys(String exportDate) {
        Set<String> keys = new HashSet<String>();
        for (Map<String, Object> e : readErrors()) {
            if (exportDate.equals(e.get("exportDate"))) {
                keys.add(e.get("employeeId") + "|" + e.get("message"));
            }
        }
        return keys;
    }

    public static File outDir() {
        return new File(System.getProperty(OUT_DIR_PROPERTY, DEFAULT_OUT_DIR));
    }

    private boolean writeOutbound(String exportDate, String controlId, String message) {
        File dir = outboundDir(exportDate);
        dir.mkdirs();
        File f = new File(dir, controlId + ".hl7");
        try {
            Files.write(f.toPath(), message.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            log.error("Outbound message {} already exists, not overwritten", f);
            return false;
        } catch (IOException e) {
            log.error("Cannot write outbound message " + f, e);
            return false;
        }
        log.debug("Queued {}", f.getName());
        return true;
    }

    private void appendErrorQueue(String exportDate, String employeeId, String message) {
        File dir = outDir();
        dir.mkdirs();
        File f = new File(dir, ERROR_QUEUE_FILE);
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
            w.println(LocalDateTime.now().format(LOG_TS) + "|" + exportDate + "|" + employeeId + "|" + message);
        } catch (IOException e) {
            log.error("Cannot append to error queue " + f, e);
        }
        log.error("{} {} {}", JOB_NAME, employeeId, message);
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
                String[] p = line.split("\\|", 4);
                if (p.length < 4) {
                    continue;
                }
                Map<String, Object> e = new LinkedHashMap<String, Object>();
                e.put("timestamp", p[0]);
                e.put("exportDate", p[1]);
                e.put("employeeId", p[2]);
                e.put("code", p[3].startsWith("AE") ? "AE" : "AR");
                e.put("message", p[3]);
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
