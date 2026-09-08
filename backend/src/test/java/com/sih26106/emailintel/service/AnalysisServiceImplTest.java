package com.sih26106.emailintel.service;

import com.sih26106.emailintel.authcheck.DkimChecker;
import com.sih26106.emailintel.authcheck.DmarcChecker;
import com.sih26106.emailintel.authcheck.SpfChecker;
import com.sih26106.emailintel.exception.AnalysisNotFoundException;
import com.sih26106.emailintel.geo.IpExtractor;
import com.sih26106.emailintel.geo.impl.MockGeolocationService;
import com.sih26106.emailintel.model.AnalysisStatus;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.parser.HopChainBuilder;
import com.sih26106.emailintel.parser.ReceivedHeaderAnalyzer;
import com.sih26106.emailintel.service.impl.AnalysisServiceImpl;
import com.sih26106.emailintel.service.impl.InMemoryAnalysisStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceImplTest {

    private AnalysisStoreService store;
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        store = new InMemoryAnalysisStoreService();
        HopChainBuilder hopChainBuilder = new HopChainBuilder(
                new ReceivedHeaderAnalyzer(), new IpExtractor(), new MockGeolocationService());
        analysisService = new AnalysisServiceImpl(store, new SpfChecker(), new DkimChecker(),
                new DmarcChecker(), hopChainBuilder);
    }

    @Test
    void analyzeRunsCheckersPopulatesAuthenticationAndMarksAnalyzed() {
        String analysisId = UUID.randomUUID().toString();

        EmailHeaders headers = new EmailHeaders();
        headers.setFrom(List.of("Alice Sender <alice@example.net>"));
        headers.setAuthenticationResults(List.of(
                "mx.target.com; spf=pass smtp.mailfrom=alice@example.net; "
                        + "dkim=pass header.d=example.net; dmarc=pass header.from=example.net"));

        EmailAnalysis stored = new EmailAnalysis();
        stored.setAnalysisId(analysisId);
        stored.setFilename("sample-2hop.eml");
        stored.setStatus(AnalysisStatus.PARSED);
        stored.setHeaders(headers);
        store.save(stored);

        EmailAnalysis result = analysisService.analyze(analysisId);

        assertEquals(AnalysisStatus.ANALYZED, result.getStatus());
        assertNotNull(result.getAuthentication());
        assertNotNull(result.getAuthentication().getSpf());
        assertNotNull(result.getAuthentication().getDkim());
        assertNotNull(result.getAuthentication().getDmarc());
        assertEquals("PASS", result.getAuthentication().getSpf().getResult().name());
        assertEquals("PASS", result.getAuthentication().getDkim().getResult().name());
        assertEquals("PASS", result.getAuthentication().getDmarc().getResult().name());

        // The updated analysis must also be the one retrievable afterward.
        assertSame(result, analysisService.getAnalysis(analysisId));
    }

    @Test
    void analyzeThrowsAnalysisNotFoundForUnknownId() {
        assertThrows(AnalysisNotFoundException.class, () -> analysisService.analyze("does-not-exist"));
    }

    @Test
    void getAnalysisThrowsAnalysisNotFoundForUnknownId() {
        assertThrows(AnalysisNotFoundException.class, () -> analysisService.getAnalysis("does-not-exist"));
    }

    @Test
    void analyzePreservesPhase1WarningsAndAppendsCheckerWarnings() {
        String analysisId = UUID.randomUUID().toString();

        EmailAnalysis stored = new EmailAnalysis();
        stored.setAnalysisId(analysisId);
        stored.setFilename("no-auth-evidence.eml");
        stored.setStatus(AnalysisStatus.PARSED);
        stored.setHeaders(new EmailHeaders()); // no SPF/DKIM/DMARC evidence at all
        stored.getWarnings().add("Phase 1 warning: could not parse Date header");
        store.save(stored);

        EmailAnalysis result = analysisService.analyze(analysisId);

        assertTrue(result.getWarnings().contains("Phase 1 warning: could not parse Date header"));
        assertTrue(result.getWarnings().size() > 1, "checker warnings should be appended, not replace existing ones");
    }

    @Test
    void analyzePopulatesHopChainWithoutBreakingAuthenticationResults() {
        String analysisId = UUID.randomUUID().toString();

        EmailHeaders headers = new EmailHeaders();
        headers.setFrom(List.of("Alice Sender <alice@example.net>"));
        headers.setAuthenticationResults(List.of("mx.target.com; spf=pass smtp.mailfrom=alice@example.net"));
        headers.setReceived(List.of(
                "from mail.example.com (mail.example.com [203.0.113.10]) "
                        + "by mx.target.com with ESMTPS id abc123; Tue, 1 Sep 2026 10:15:20 +0000",
                "from smtp.example.net (smtp.example.net [198.51.100.20]) "
                        + "by mail.example.com with ESMTP id def456; Tue, 1 Sep 2026 10:10:00 +0000"));

        EmailAnalysis stored = new EmailAnalysis();
        stored.setAnalysisId(analysisId);
        stored.setFilename("sample-2hop.eml");
        stored.setStatus(AnalysisStatus.PARSED);
        stored.setHeaders(headers);
        store.save(stored);

        EmailAnalysis result = analysisService.analyze(analysisId);

        // Authentication results must remain intact alongside the new hop chain.
        assertEquals("PASS", result.getAuthentication().getSpf().getResult().name());

        assertEquals(2, result.getHopChain().size());
        // Chronological order: smtp.example.net (10:10) is older, must come first.
        assertEquals("smtp.example.net", result.getHopChain().get(0).getSourceHostname());
        assertEquals("198.51.100.20", result.getHopChain().get(0).getSourceIp());
        assertEquals("mail.example.com", result.getHopChain().get(1).getSourceHostname());
        assertEquals(1, result.getHopChain().get(0).getHopNumber());
        assertEquals(2, result.getHopChain().get(1).getHopNumber());
        assertEquals(AnalysisStatus.ANALYZED, result.getStatus());
    }
}
