package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.AlignmentStatus;
import com.sih26106.emailintel.model.DkimResult;
import com.sih26106.emailintel.model.DmarcResultCode;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.SpfResult;
import com.sih26106.emailintel.model.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DmarcCheckerTest {

    private final DmarcChecker checker = new DmarcChecker();

    @Test
    void reportsPassAndAlignedWhenDomainsMatch() {
        EmailHeaders headers = new EmailHeaders();
        headers.setFrom(List.of("Alice Sender <alice@example.net>"));
        headers.setAuthenticationResults(List.of("mx.target.com; dmarc=pass header.from=example.net"));

        SpfResult spf = new SpfResult();
        spf.setDomain("example.net");
        DkimResult dkim = new DkimResult();
        dkim.setSigningDomain("example.net");

        var result = checker.check(headers, spf, dkim);

        assertEquals(DmarcResultCode.PASS, result.getResult());
        assertEquals("example.net", result.getFromDomain());
        assertEquals(AlignmentStatus.ALIGNED, result.getSpfAligned());
        assertEquals(AlignmentStatus.ALIGNED, result.getDkimAligned());
        assertEquals(VerificationMethod.REPORTED_BY_RECEIVING_SERVER, result.getVerificationMethod());
    }

    @Test
    void reportsFailAndNotAlignedWhenDomainsMismatch() {
        EmailHeaders headers = new EmailHeaders();
        headers.setFrom(List.of("Attacker <attacker@evil.com>"));
        headers.setAuthenticationResults(List.of("mx.target.com; dmarc=fail header.from=evil.com"));

        SpfResult spf = new SpfResult();
        spf.setDomain("unrelated.net");
        DkimResult dkim = new DkimResult();
        dkim.setSigningDomain("also-unrelated.net");

        var result = checker.check(headers, spf, dkim);

        assertEquals(DmarcResultCode.FAIL, result.getResult());
        assertEquals(AlignmentStatus.NOT_ALIGNED, result.getSpfAligned());
        assertEquals(AlignmentStatus.NOT_ALIGNED, result.getDkimAligned());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void returnsUnknownAndNotPerformedWhenNoEvidenceAtAll() {
        EmailHeaders headers = new EmailHeaders();
        headers.setFrom(List.of("Someone <someone@example.org>"));

        var result = checker.check(headers, null, null);

        assertEquals(DmarcResultCode.UNKNOWN, result.getResult());
        assertEquals("NONE", result.getSource());
        assertEquals(VerificationMethod.NOT_PERFORMED, result.getVerificationMethod());
        assertEquals(AlignmentStatus.UNKNOWN, result.getSpfAligned());
        assertEquals(AlignmentStatus.UNKNOWN, result.getDkimAligned());
    }

    @Test
    void handlesMissingFromHeaderGracefully() {
        EmailHeaders headers = new EmailHeaders(); // no From set at all

        var result = checker.check(headers, null, null);

        assertNull(result.getFromDomain());
        assertFalse(result.getWarnings().isEmpty());
    }
}
