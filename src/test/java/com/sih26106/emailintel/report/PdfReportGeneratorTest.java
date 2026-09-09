package com.sih26106.emailintel.report;

import com.sih26106.emailintel.ioc.IocFormatter;
import com.sih26106.emailintel.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
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
 
class PdfReportGeneratorTest {

    @Mock IocFormatter iocFormatter;
    @InjectMocks PdfReportGenerator gen;

    @BeforeEach void setup() { when(iocFormatter.formatGrouped(any())).thenReturn(Map.of()); }

    @Test void testProducesPdf() {
        byte[] pdf = gen.generate(analysis());
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test void testNullAnalysis() { assertThat(gen.generate(null)).isEmpty(); }

    @Test void testNullRiskReport() {
        EmailAnalysis a = analysis();
        a.setRiskReport(null);
        assertThat(gen.generate(a)).isNotEmpty(); // degrades gracefully
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
                .analyzedAt(Instant.now()).riskReport(r).iocs(List.of())
                .hopEvents(List.of()).build();
    }
}
