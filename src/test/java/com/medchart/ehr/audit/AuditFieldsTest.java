package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFieldsTest {

    @Test
    void boundedCapsLengthAndDropsControlCharacters() {
        assertThat(AuditFields.bounded("x".repeat(300), 200)).hasSize(200);
        assertThat(AuditFields.bounded("a\u0000b\r\nc", 10)).isEqualTo("abc");
        assertThat(AuditFields.bounded(null, 10)).isNull();
    }

    @Test
    void boundedNeverSplitsSurrogatePair() {
        String value = "a".repeat(4) + "\uD83D\uDE00";
        assertThat(AuditFields.bounded(value, 5)).isEqualTo("aaaa");
        assertThat(AuditFields.bounded(value, 6)).isEqualTo(value);
    }

    @Test
    void boundedDropsUnpairedSurrogates() {
        assertThat(AuditFields.bounded("a\uD83Db\uDE00c\uD83D", 10)).isEqualTo("abc");
    }

    @Test
    void ipLiteralAcceptsOnlyAddresses() {
        assertThat(AuditFields.ipLiteral(" 203.0.113.7 ")).isEqualTo("203.0.113.7");
        assertThat(AuditFields.ipLiteral("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(AuditFields.ipLiteral("::ffff:192.0.2.1")).isEqualTo("::ffff:192.0.2.1");
        assertThat(AuditFields.ipLiteral("256.1.1.1")).isNull();
        assertThat(AuditFields.ipLiteral("example.com")).isNull();
        assertThat(AuditFields.ipLiteral("1".repeat(100))).isNull();
        assertThat(AuditFields.ipLiteral("abc:::def:xyz")).isNull();
        assertThat(AuditFields.ipLiteral("")).isNull();
    }
}
