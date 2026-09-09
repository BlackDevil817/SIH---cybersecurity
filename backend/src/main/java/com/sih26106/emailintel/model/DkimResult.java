package com.sih26106.emailintel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured DKIM evidence for one email.
 *
 * IMPORTANT: this module does NOT perform cryptographic DKIM signature verification.
 * `result` reflects the dkim= verdict already reported in Authentication-Results by the
 * receiving server, if present. Metadata (signingDomain, selector, etc.) may come from the
 * raw DKIM-Signature header even when no verdict is available - see `verificationMethod`.
 */
public class DkimResult {

    private DkimResultCode result = DkimResultCode.UNKNOWN;
    /** d= tag: the domain that claims to have signed the message. */
    private String signingDomain;
    /** s= tag: the DKIM selector. */
    private String selector;
    /** a= tag: signing algorithm, e.g. rsa-sha256. */
    private String algorithm;
    /** c= tag: canonicalization, e.g. relaxed/relaxed. */
    private String canonicalization;
    /** h= tag: colon-separated list of signed header names. */
    private String headerList;
    /** i= tag: the Agent or User Identifier (AUID), if present. */
    private String identity;
    /** Which header(s) this evidence came from: "Authentication-Results", "DKIM-Signature", or "NONE". */
    private String source;
    private String rawEvidence;
    private VerificationMethod verificationMethod = VerificationMethod.NOT_PERFORMED;
    private List<String> warnings = new ArrayList<>();

    public DkimResultCode getResult() {
        return result;
    }

    public void setResult(DkimResultCode result) {
        this.result = result;
    }

    public String getSigningDomain() {
        return signingDomain;
    }

    public void setSigningDomain(String signingDomain) {
        this.signingDomain = signingDomain;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCanonicalization() {
        return canonicalization;
    }

    public void setCanonicalization(String canonicalization) {
        this.canonicalization = canonicalization;
    }

    public String getHeaderList() {
        return headerList;
    }

    public void setHeaderList(String headerList) {
        this.headerList = headerList;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
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
