package com.medchart.ehr.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MrnGeneratorTest {

    private final MrnGenerator generator = new MrnGenerator();

    @Test
    void generatesOpaqueFixedLengthMrn() {
        String mrn = generator.generate();

        assertThat(mrn).matches("MRN[0-9A-F]{16}");
        assertThat(mrn.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void generatesUnpredictableNonSequentialValues() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(generator.generate());
        }

        assertThat(generated).hasSize(1000);

        String now = String.valueOf(System.currentTimeMillis());
        String millisPrefix = now.substring(0, now.length() - 4);
        assertThat(generated).noneMatch(mrn -> mrn.contains(millisPrefix));
    }
}
