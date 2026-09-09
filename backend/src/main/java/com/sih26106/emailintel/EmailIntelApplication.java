package com.sih26106.emailintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Email Threat Detection / Forensic Intelligence backend module.
 *
 * Scope of this module (per team split): EML ingestion, header extraction,
 * SPF/DKIM/DMARC analysis, Received-header hop-chain construction and geo-tagging.
 * ML clustering/classification and the frontend are owned by other sub-teams and are
 * NOT implemented here - this application only exposes clean data contracts for them.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EmailIntelApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailIntelApplication.class, args);
    }
}
