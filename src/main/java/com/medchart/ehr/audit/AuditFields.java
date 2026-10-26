package com.medchart.ehr.audit;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Normalizes caller-influenced values before they are written to audit_events,
 * so an oversized or malformed value can never make the audit insert fail.
 */
public final class AuditFields {

    public static final String INVALID_IP = "invalid";

    private static final int MAX_IP_LITERAL_LENGTH = 45;
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private AuditFields() {
    }

    /** Removes control characters (PostgreSQL rejects NUL in text) and caps the value at {@code max} chars. */
    public static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), max));
        for (int i = 0; i < value.length() && sb.length() < max; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                continue;
            }
            if (Character.isHighSurrogate(c) && sb.length() == max - 1) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Returns the value if it is an IPv4/IPv6 literal, otherwise {@code null}. Never performs DNS lookups. */
    public static String ipLiteral(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || candidate.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        if (IPV4.matcher(candidate).matches()) {
            return candidate;
        }
        if (candidate.indexOf(':') >= 0 && IPV6_CHARS.matcher(candidate).matches()) {
            try {
                InetAddress.getByName(candidate);
                return candidate;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
