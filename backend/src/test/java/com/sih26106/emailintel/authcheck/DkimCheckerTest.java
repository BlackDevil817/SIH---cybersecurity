package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.DkimResultCode;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DkimCheckerTest {

    private final DkimChecker checker = new DkimChecker();

    @Test
    void reportsPassFromAuthenticationResults() {
        EmailHeaders headers = new EmailHeaders();
        headers.setAuthenticationResults(List.of("mx.target.com; dkim=pass header.d=example.net"));

        var result = checker.check(headers);

        assertEquals(DkimResultCode.PASS, result.getResult());
        assertEquals("example.net", result.getSigningDomain());
        assertEquals(VerificationMethod.REPORTED_BY_RECEIVING_SERVER, result.getVerificationMethod());
    }

    @Test
    void reportsFailFromAuthenticationResults() {
        EmailHeaders headers = new EmailHeaders();
        headers.setAuthenticationResults(List.of("mx.target.com; dkim=fail header.d=spoofed.net"));

        var result = checker.check(headers);

        assertEquals(DkimResultCode.FAIL, result.getResult());
        assertEquals("spoofed.net", result.getSigningDomain());
    }

    @Test
    void extractsMetadataButMarksUnknownWhenSignaturePresentWithoutVerdict() {
        EmailHeaders headers = new EmailHeaders();
        headers.setDkimSignatures(List.of(
                "v=1; a=rsa-sha256; d=example.net; s=sel1; c=relaxed/relaxed; h=from:to:subject"));

        var result = checker.check(headers);

        assertEquals(DkimResultCode.UNKNOWN, result.getResult());
        assertEquals("example.net", result.getSigningDomain());
        assertEquals("sel1", result.getSelector());
        assertEquals("rsa-sha256", result.getAlgorithm());
        assertEquals("relaxed/relaxed", result.getCanonicalization());
        assertEquals(VerificationMethod.NOT_PERFORMED, result.getVerificationMethod());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void returnsNoneWhenNoEvidenceAtAll() {
        EmailHeaders headers = new EmailHeaders();

        var result = checker.check(headers);

        assertEquals(DkimResultCode.NONE, result.getResult());
        assertEquals("NONE", result.getSource());
        assertEquals(VerificationMethod.NOT_PERFORMED, result.getVerificationMethod());
    }

    @Test
    void gracefullyHandlesMultipleDkimSignatureHeaders() {
        EmailHeaders headers = new EmailHeaders();
        headers.setDkimSignatures(List.of(
                "v=1; a=rsa-sha256; d=first.net; s=sel1",
                "v=1; a=rsa-sha256; d=second.net; s=sel2"));

        var result = checker.check(headers);

        assertEquals("first.net", result.getSigningDomain());
        assertFalse(result.getWarnings().isEmpty());
    }
}
