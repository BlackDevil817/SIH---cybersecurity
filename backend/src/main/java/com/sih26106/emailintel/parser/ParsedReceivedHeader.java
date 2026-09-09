package com.sih26106.emailintel.parser;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal, best-effort structured view of ONE raw "Received:" header, produced by
 * ReceivedHeaderAnalyzer and consumed by HopChainBuilder.
 *
 * This is intentionally kept separate from the public HopEvent model: this class captures
 * whatever the analyzer could parse out of the raw text BEFORE IP extraction/classification
 * and geolocation are applied - HopEvent is the final, fully-enriched, externally-consumed
 * result. Not exposed via any controller/DTO.
 */
public class ParsedReceivedHeader {

    /** Position in EmailHeaders.getReceived() - index 0 is the topmost (most recent) header. */
    private int originalIndex;

    private String rawHeader;

    /** Best-effort leading hostname token from the "from" clause, e.g. "mail.example.com". */
    private String fromHost;
    /** Full text of the "from" clause, e.g. "mail.example.com (mail.example.com [203.0.113.10])" -
     *  kept so IpExtractor can search it for an embedded IP address. */
    private String fromRaw;

    /** Best-effort leading hostname token from the "by" clause. */
    private String byHost;
    /** Full text of the "by" clause. */
    private String byRaw;

    /** Token following "with", e.g. "ESMTPS", "ESMTP", "SMTP". Null if not present. */
    private String protocol;

    /** Raw trailing date-time text (after the last ';'). Null if the header has none. */
    private String rawTimestamp;
    /** Parsed timestamp, normalized to UTC. Null if missing/unparseable - never fabricated. */
    private OffsetDateTime parsedTimestamp;

    private List<String> warnings = new ArrayList<>();

    public int getOriginalIndex() {
        return originalIndex;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = originalIndex;
    }

    public String getRawHeader() {
        return rawHeader;
    }

    public void setRawHeader(String rawHeader) {
        this.rawHeader = rawHeader;
    }

    public String getFromHost() {
        return fromHost;
    }

    public void setFromHost(String fromHost) {
        this.fromHost = fromHost;
    }

    public String getFromRaw() {
        return fromRaw;
    }

    public void setFromRaw(String fromRaw) {
        this.fromRaw = fromRaw;
    }

    public String getByHost() {
        return byHost;
    }

    public void setByHost(String byHost) {
        this.byHost = byHost;
    }

    public String getByRaw() {
        return byRaw;
    }

    public void setByRaw(String byRaw) {
        this.byRaw = byRaw;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getRawTimestamp() {
        return rawTimestamp;
    }

    public void setRawTimestamp(String rawTimestamp) {
        this.rawTimestamp = rawTimestamp;
    }

    public OffsetDateTime getParsedTimestamp() {
        return parsedTimestamp;
    }

    public void setParsedTimestamp(OffsetDateTime parsedTimestamp) {
        this.parsedTimestamp = parsedTimestamp;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
