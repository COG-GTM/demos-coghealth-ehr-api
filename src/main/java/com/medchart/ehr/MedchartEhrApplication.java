package com.medchart.ehr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the MedChart EHR API.
 *
 * Bootstraps the Spring Boot application with async support
 * enabled for non-blocking audit logging and notifications.
 */
@SpringBootApplication
@EnableAsync
public class MedchartEhrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedchartEhrApplication.class, args);
    }
}
