package com.sih26106.emailintel.report;

import com.sih26106.emailintel.ioc.IocFormatter;
import com.sih26106.emailintel.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)

class HtmlReportGeneratorTest {

    @Mock IocFormatter iocFormatter;
    @InjectMocks HtmlReportGenerator gen;

    @BeforeEach void setup() { when(iocFormatter.formatGrouped(any())).thenReturn(Map.of()); }

    @Test void testBasicGeneration() {
        String html = gen.generate(analysis());
        assertThat(html).contains("<!DOCTYPE html>", "Email Intelligence", "evil.com");
    }

    @Test void testNullAnalysis() {
        assertThat(gen.generate(null)).contains("No analysis data available");
    }

    @Test void testXssEscaping() {
        EmailAnalysis a = analysis();
        a.setSubject("<script>alert('xss')</script>");
        String html = gen.generate(a);
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test void testMlUnavailable() {
        assertThat(gen.generate(analysis())).contains("unavailable");
    }

    @Test void testMlResultsShown() {
        EmailAnalysis a = analysis();
        a.getRiskReport().setClusterId("c-abc");
        a.getRiskReport().setClusterLabel("phishing");
        a.getRiskReport().setClusteringConfidence(0.94);
        String html = gen.generate(a);
        assertThat(html).contains("c-abc", "phishing");
    }

    private EmailAnalysis analysis() {
        RiskBreakdown bd = RiskBreakdown.builder()
                .authenticationScore(80).senderScore(50).domainScore(40)
                .networkScore(30).contentScore(60).mlScore(0).build();
        RiskReport r = RiskReport.builder().overallRiskScore(75).riskLevel("HIGH")
                .riskBreakdown(bd).generatedAt(Instant.now()).build();
        return EmailAnalysis.builder().id(1L).fileName("t.eml").subject("Test")
                .fromAddress("a@evil.com").senderDomain("evil.com").senderIp("1.2.3.4")
                .spfResult("FAIL").dkimResult("FAIL").dmarcResult("FAIL")
                .analyzedAt(Instant.now()).riskReport(r).iocs(List.of()).build();
    }
}

