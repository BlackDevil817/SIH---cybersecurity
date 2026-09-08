package com.sih26106.emailintel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured SPF evidence for one email, as reported by the receiving mail server.
 *
 * IMPORTANT: this module does NOT perform its own SPF DNS lookup. `result` reflects
 * what was already recorded in Authentication-Results or Received-SPF headers.
 * See `verificationMethod` for exactly how this was obtained.
 */
public class SpfResult {

    private SpfResultCode result = SpfResultCode.UNKNOWN;
    private String domain;
    private String ip;
    /** Which header this evidence came from: "Authentication-Results", "Received-SPF", or "NONE". */
    private String source;
    /** The raw header fragment this result was parsed from, for forensic traceability. */
    private String rawEvidence;
    private VerificationMethod verificationMethod = VerificationMethod.NOT_PERFORMED;
    private List<String> warnings = new ArrayList<>();

    public SpfResultCode getResult() {
        return result;
    }

    public void setResult(SpfResultCode result) {
        this.result = result;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawEvidence() {
        return rawEvidence;
    }

    public void setRawEvidence(String rawEvidence) {
        this.rawEvidence = rawEvidence;
    }

    public VerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(VerificationMethod verificationMethod) {
        this.verificationMethod = verificationMethod;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
