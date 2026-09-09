package com.sih26106.emailintel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured DMARC evidence for one email.
 *
 * IMPORTANT: this module does NOT fetch the domain's DMARC DNS TXT record. `result` and
 * `policy` reflect only what the receiving server already reported in Authentication-Results.
 * `spfAligned`/`dkimAligned` are computed here by comparing the From domain to the SPF/DKIM
 * domains already parsed elsewhere - using STRICT (exact-match) alignment only.
 */
public class DmarcResult {

    private DmarcResultCode result = DmarcResultCode.UNKNOWN;
    /** Domain parsed from the visible From: header (the "RFC5322.From" domain). */
    private String fromDomain;
    private AlignmentStatus spfAligned = AlignmentStatus.UNKNOWN;
    private AlignmentStatus dkimAligned = AlignmentStatus.UNKNOWN;
    /** Published policy (p=) if the receiving server happened to include it in its comment - often absent. */
    private String policy;
    private String source;
    private String rawEvidence;
    private VerificationMethod verificationMethod = VerificationMethod.NOT_PERFORMED;
    private List<String> warnings = new ArrayList<>();

    public DmarcResultCode getResult() {
        return result;
    }

    public void setResult(DmarcResultCode result) {
        this.result = result;
    }

    public String getFromDomain() {
        return fromDomain;
    }

    public void setFromDomain(String fromDomain) {
        this.fromDomain = fromDomain;
    }

    public AlignmentStatus getSpfAligned() {
        return spfAligned;
    }

    public void setSpfAligned(AlignmentStatus spfAligned) {
        this.spfAligned = spfAligned;
    }

    public AlignmentStatus getDkimAligned() {
        return dkimAligned;
    }

    public void setDkimAligned(AlignmentStatus dkimAligned) {
        this.dkimAligned = dkimAligned;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
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
