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

    private static final String SPLIT_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";

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
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 || line.trim().length() == 0) {
                    continue;
                }
                String[] f = line.split(SPLIT_REGEX, -1);
                if (f.length < 9) {
                    log.warn("Skipping malformed line {} in {}: {}", lineNo, fileName, line);
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

    private static String clean(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }
}
