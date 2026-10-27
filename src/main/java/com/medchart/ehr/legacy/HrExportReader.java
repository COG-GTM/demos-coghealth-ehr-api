package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class HrExportReader {

    public static final String EXPORT_DIR = "interfaces/hr/export";
    public static final String FILE_PREFIX = "workday_pto_export_";
    public static final String FILE_SUFFIX = ".csv";
    public static final String DATE_PROPERTY = "hr.export.date";
    public static final String DIR_PROPERTY = "hr.export.dir";

    public static final int MAX_LINE_LENGTH = 4096;
    public static final int MAX_FIELDS = 32;

    public List<String> listExportDates() {
        List<String> dates = new ArrayList<String>();
        String dir = System.getProperty(DIR_PROPERTY);
        if (dir != null && dir.length() > 0) {
            File[] files = new File(dir).listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (n.startsWith(FILE_PREFIX) && n.endsWith(FILE_SUFFIX)) {
                        dates.add(n.substring(FILE_PREFIX.length(), n.length() - FILE_SUFFIX.length()));
                    }
                }
            }
        } else {
            try {
                Resource[] resources = new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:" + EXPORT_DIR + "/" + FILE_PREFIX + "*" + FILE_SUFFIX);
                for (Resource r : resources) {
                    String n = r.getFilename();
                    if (n != null) {
                        dates.add(n.substring(FILE_PREFIX.length(), n.length() - FILE_SUFFIX.length()));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot list HR exports", e);
            }
        }
        Collections.sort(dates);
        return dates;
    }

    public String latestExportDate() {
        List<String> dates = listExportDates();
        if (dates.isEmpty()) {
            throw new IllegalStateException("No HR export files found under " + EXPORT_DIR);
        }
        return dates.get(dates.size() - 1);
    }

    public String resolveExportDate(String requested) {
        if (requested == null || requested.trim().length() == 0 || requested.startsWith("${")
                || "latest".equalsIgnoreCase(requested.trim())) {
            return latestExportDate();
        }
        return requested.trim();
    }

    public List<HrPtoRecord> read(String exportDate) {
        String fileName = FILE_PREFIX + exportDate + FILE_SUFFIX;
        List<HrPtoRecord> out = new ArrayList<HrPtoRecord>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(open(fileName), "UTF-8"))) {
            StringBuilder buf = new StringBuilder();
            int lineNo = 0;
            int status;
            while ((status = readLine(br, buf, MAX_LINE_LENGTH)) != EOF) {
                lineNo++;
                if (status == TOO_LONG) {
                    log.warn("Skipping line {} in {}: longer than {} characters", lineNo, fileName, MAX_LINE_LENGTH);
                    continue;
                }
                String line = buf.toString();
                if (lineNo == 1 || line.trim().length() == 0) {
                    continue;
                }
                String[] f = splitLine(line);
                if (f == null || f.length < 9) {
                    log.warn("Skipping malformed line {} in {} ({} characters)", lineNo, fileName, line.length());
                    continue;
                }
                HrPtoRecord r = new HrPtoRecord();
                r.lineNumber = lineNo;
                r.employeeId = clean(f[0]);
                r.lastName = clean(f[1]);
                r.firstName = clean(f[2]);
                r.npi = clean(f[3]);
                r.costCenter = clean(f[4]);
                r.ptoStart = clean(f[5]);
                r.ptoEnd = clean(f[6]);
                r.ptoType = clean(f[7]);
                r.status = clean(f[8]);
                r.notes = f.length > 9 ? clean(f[9]) : "";
                out.add(r);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read HR export " + fileName, e);
        }
        log.info("Read {} records from {}", out.size(), fileName);
        return out;
    }

    private InputStream open(String fileName) throws IOException {
        String dir = System.getProperty(DIR_PROPERTY);
        if (dir != null && dir.length() > 0) {
            return new FileInputStream(new File(dir, fileName));
        }
        InputStream in = HrExportReader.class.getClassLoader().getResourceAsStream(EXPORT_DIR + "/" + fileName);
        if (in == null) {
            throw new IOException("HR export not found: " + EXPORT_DIR + "/" + fileName);
        }
        return in;
    }

    static final int EOF = -1;
    static final int OK = 0;
    static final int TOO_LONG = 1;

    /**
     * Reads one line (terminated by LF, CR or CRLF) into {@code buf} without buffering more than
     * {@code maxLength} characters; the rest of an over-long line is consumed and discarded.
     */
    static int readLine(Reader in, StringBuilder buf, int maxLength) throws IOException {
        buf.setLength(0);
        boolean tooLong = false;
        int c = in.read();
        if (c < 0) {
            return EOF;
        }
        while (c >= 0 && c != '\n' && c != '\r') {
            if (buf.length() < maxLength) {
                buf.append((char) c);
            } else {
                tooLong = true;
            }
            c = in.read();
        }
        if (c == '\r') {
            in.mark(1);
            if (in.read() != '\n') {
                in.reset();
            }
        }
        return tooLong ? TOO_LONG : OK;
    }

    /**
     * Splits a CSV line on commas outside double quotes in a single linear pass. Fields keep their quotes
     * (stripped by {@link #clean}). Returns null for unbalanced quotes or more than {@link #MAX_FIELDS} fields.
     */
    static String[] splitLine(String line) {
        List<String> fields = new ArrayList<String>();
        boolean inQuotes = false;
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                if (fields.size() == MAX_FIELDS - 1) {
                    return null;
                }
                fields.add(line.substring(start, i));
                start = i + 1;
            }
        }
        if (inQuotes) {
            return null;
        }
        fields.add(line.substring(start));
        return fields.toArray(new String[0]);
    }

    private static String clean(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }
}
