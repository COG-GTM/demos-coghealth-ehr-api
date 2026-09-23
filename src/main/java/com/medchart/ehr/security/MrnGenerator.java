package com.medchart.ehr.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates opaque medical record numbers. MRNs are treated as public-facing
 * identifiers, so they must not be guessable or derivable from one another.
 */
@Component
public class MrnGenerator {

    private static final String PREFIX = "MRN";
    private static final int RANDOM_BYTES = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);

        StringBuilder sb = new StringBuilder(PREFIX);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
