package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.SpfResultCode;
import com.sih26106.emailintel.model.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpfCheckerTest {

    private final SpfChecker checker = new SpfChecker();

    @Test
    void reportsPassFromAuthenticationResults() {
        EmailHeaders headers = new EmailHeaders();
        headers.setAuthenticationResults(List.of(
                "mx.target.com; spf=pass smtp.mailfrom=sender@example.net"));

        var result = checker.check(headers);

        assertEquals(SpfResultCode.PASS, result.getResult());
        assertEquals("example.net", result.getDomain());
        assertEquals("Authentication-Results", result.getSource());
        assertEquals(VerificationMethod.REPORTED_BY_RECEIVING_SERVER, result.getVerificationMethod());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void reportsFailFromReceivedSpfWhenNoAuthenticationResults() {
        EmailHeaders headers = new EmailHeaders();
        headers.setReceivedSpf(List.of(
                "fail (mx.target.com: domain of spoofed@evil.com does not designate 1.2.3.4 as permitted sender) client-ip=1.2.3.4;"));

        var result = checker.check(headers);

        assertEquals(SpfResultCode.FAIL, result.getResult());
        assertEquals("1.2.3.4", result.getIp());
        assertEquals("evil.com", result.getDomain());
        assertEquals("Received-SPF", result.getSource());
        assertEquals(VerificationMethod.REPORTED_BY_RECEIVING_SERVER, result.getVerificationMethod());
    }

    @Test
    void reportsSoftfailFromAuthenticationResults() {
        EmailHeaders headers = new EmailHeaders();
        headers.setAuthenticationResults(List.of(
                "mx.target.com; spf=softfail smtp.mailfrom=sender@marginal.net"));

        var result = checker.check(headers);

        assertEquals(SpfResultCode.SOFTFAIL, result.getResult());
        assertEquals("marginal.net", result.getDomain());
    }

    @Test
    void returnsUnknownAndNotPerformedWhenNoEvidenceAtAll() {
        EmailHeaders headers = new EmailHeaders();

        var result = checker.check(headers);

        assertEquals(SpfResultCode.UNKNOWN, result.getResult());
        assertEquals("NONE", result.getSource());
        assertEquals(VerificationMethod.NOT_PERFORMED, result.getVerificationMethod());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void gracefullyHandlesMultipleAuthenticationResultsHeaders() {
        EmailHeaders headers = new EmailHeaders();
        headers.setAuthenticationResults(List.of(
                "mx1.target.com; spf=pass smtp.mailfrom=a@first.com",
                "mx2.target.com; spf=fail smtp.mailfrom=b@second.com"));

        var result = checker.check(headers);

        // First matching header wins; a warning notes the ambiguity rather than crashing.
        assertEquals(SpfResultCode.PASS, result.getResult());
        assertEquals("first.com", result.getDomain());
        assertFalse(result.getWarnings().isEmpty());
    }
}
