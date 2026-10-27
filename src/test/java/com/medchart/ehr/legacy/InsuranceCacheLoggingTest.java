package com.medchart.ehr.legacy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsuranceCacheLoggingTest {

    private static final String MRN = "MRN-0042-ALICE";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(InsuranceCache.class);
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void cacheLogsMaskedMrnOnly() {
        InsuranceCache cache = new InsuranceCache();
        cache.cacheEligibility(MRN, "000-00-0000", "AETNA", "M1", true, "PPO", "20", "500");
        cache.getEligibility(MRN, "AETNA");

        assertEquals(2, appender.list.size());
        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains(MRN), message);
            assertTrue(message.contains("****LICE"), message);
        }
    }
}
