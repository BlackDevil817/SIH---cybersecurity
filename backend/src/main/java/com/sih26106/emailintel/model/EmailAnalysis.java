package com.sih26106.emailintel.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level forensic result for one uploaded email.
 *
 * This is the contract shared with the ML team and the frontend team:
 *   - ML consumes: headers (from/senderDomain/senderIp derivable), authentication, hopChain, iocs
 *   - Frontend consumes: hopChain (for the attack-route animation)
 *
 * PHASE 1: only analysisId, filename, status, headers, timestamps and warnings are populated.
 * `authentication`, `hopChain`, `iocs` are present in the contract now (non-null, empty/default)
 * so downstream teams can start integrating against the shape immediately; they are filled in
 * by later phases (SPF/DKIM/DMARC checkers, HopChainBuilder, IOC extractor) without requiring
 * any change to this class or to the JSON shape already being consumed.
 */
public class EmailAnalysis {

    private String analysisId;
    private String filename;
    private AnalysisStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private EmailHeaders headers;

    private AuthenticationSummary authentication = new AuthenticationSummary();
    private List<HopEvent> hopChain = new ArrayList<>();
    private List<Ioc> iocs = new ArrayList<>();

    /** Aggregated warnings from every stage of the pipeline that has run so far. */
    private List<String> warnings = new ArrayList<>();

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public EmailHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(EmailHeaders headers) {
        this.headers = headers;
    }

    public AuthenticationSummary getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AuthenticationSummary authentication) {
        this.authentication = authentication;
    }

    public List<HopEvent> getHopChain() {
        return hopChain;
    }

    public void setHopChain(List<HopEvent> hopChain) {
        this.hopChain = hopChain;
    }

    public List<Ioc> getIocs() {
        return iocs;
    }

    public void setIocs(List<Ioc> iocs) {
        this.iocs = iocs;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
