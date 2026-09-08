package com.sih26106.emailintel.model;

/**
 * PHASE 1 STUB (extraction logic implemented in Phase 3).
 * A single Indicator of Compromise found while parsing the email.
 */
public class Ioc {

    public enum IocType {
        IP, DOMAIN, EMAIL, URL, MESSAGE_ID, INFRASTRUCTURE
    }

    private IocType type;
    private String value;
    /** Which header/section this IOC was derived from, e.g. "Received", "From". */
    private String source;
    /** 0.0-1.0 confidence in this extraction. */
    private double confidence;

    public Ioc() {
    }

    public Ioc(IocType type, String value, String source, double confidence) {
        this.type = type;
        this.value = value;
        this.source = source;
        this.confidence = confidence;
    }

    public IocType getType() {
        return type;
    }

    public void setType(IocType type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
