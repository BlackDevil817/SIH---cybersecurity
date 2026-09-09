package com.sih26106.emailintel.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalized, structured view of an email's headers.
 *
 * CRITICAL INVARIANT: headers that can legally repeat in RFC 5322 / RFC 5321
 * (Received, Authentication-Results, DKIM-Signature, Received-SPF, ARC-*) are
 * preserved as ORDERED lists exactly as encountered in the source file.
 * Nothing here overwrites a repeated header - that would destroy forensic evidence
 * needed later by ReceivedHeaderAnalyzer / HopChainBuilder.
 *
 * Ordering convention for `received`: index 0 is the header that appears FIRST in the
 * raw file (i.e. the most recent hop, added last by the final receiving server).
 * This is the raw/topological order, NOT the chronological order - HopChainBuilder
 * (Phase 3) is responsible for re-deriving chronological order from timestamps.
 */
public class EmailHeaders {

    private List<String> from = new ArrayList<>();
    private List<String> to = new ArrayList<>();
    private List<String> cc = new ArrayList<>();
    private List<String> replyTo = new ArrayList<>();

    private String subject;

    private String rawDate;
    private OffsetDateTime parsedDate; // null if missing/unparseable - never fabricated

    private String messageId;

    private List<String> returnPath = new ArrayList<>();

    /** Ordered, raw Received header values - most recent (topmost in file) first. */
    private List<String> received = new ArrayList<>();

    private List<String> authenticationResults = new ArrayList<>();
    private List<String> dkimSignatures = new ArrayList<>();
    private List<String> receivedSpf = new ArrayList<>();
    private List<String> arcHeaders = new ArrayList<>();

    public List<String> getFrom() {
        return from;
    }

    public void setFrom(List<String> from) {
        this.from = from;
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public List<String> getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(List<String> replyTo) {
        this.replyTo = replyTo;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRawDate() {
        return rawDate;
    }

    public void setRawDate(String rawDate) {
        this.rawDate = rawDate;
    }

    public OffsetDateTime getParsedDate() {
        return parsedDate;
    }

    public void setParsedDate(OffsetDateTime parsedDate) {
        this.parsedDate = parsedDate;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<String> getReturnPath() {
        return returnPath;
    }

    public void setReturnPath(List<String> returnPath) {
        this.returnPath = returnPath;
    }

    public List<String> getReceived() {
        return received;
    }

    public void setReceived(List<String> received) {
        this.received = received;
    }

    public List<String> getAuthenticationResults() {
        return authenticationResults;
    }

    public void setAuthenticationResults(List<String> authenticationResults) {
        this.authenticationResults = authenticationResults;
    }

    public List<String> getDkimSignatures() {
        return dkimSignatures;
    }

    public void setDkimSignatures(List<String> dkimSignatures) {
        this.dkimSignatures = dkimSignatures;
    }

    public List<String> getReceivedSpf() {
        return receivedSpf;
    }

    public void setReceivedSpf(List<String> receivedSpf) {
        this.receivedSpf = receivedSpf;
    }

    public List<String> getArcHeaders() {
        return arcHeaders;
    }

    public void setArcHeaders(List<String> arcHeaders) {
        this.arcHeaders = arcHeaders;
    }
}
