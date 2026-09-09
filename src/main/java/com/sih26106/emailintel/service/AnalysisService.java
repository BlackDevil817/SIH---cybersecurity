package com.sih26106.emailintel.service;

import com.sih26106.emailintel.authcheck.*;
import com.sih26106.emailintel.exception.EmailParsingException;
import com.sih26106.emailintel.fingerprint.CampaignMatchService;
import com.sih26106.emailintel.ioc.UrlExtractor;
import com.sih26106.emailintel.model.*;
import com.sih26106.emailintel.parser.*;
import com.sih26106.emailintel.repository.EmailAnalysisRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final EmlParser emlParser;
    private final HopChainBuilder hopChainBuilder;
    private final SpfChecker spfChecker;
    private final DkimChecker dkimChecker;
    private final DmarcChecker dmarcChecker;
    private final UrlExtractor urlExtractor;
    private final CampaignMatchService campaignMatchService;
    private final EmailAnalysisRepository analysisRepository;

    @Transactional
    public EmailAnalysis analyze(MultipartFile file) {
        log.info("AnalysisService: processing '{}'", file.getOriginalFilename());

        // ── 1. Parse .eml
        EmailAnalysis analysis;
        try (var is = file.getInputStream()) {
            analysis = emlParser.parse(is, file.getOriginalFilename());
        } catch (Exception ex) {
            throw new EmailParsingException("Cannot read file: " + file.getOriginalFilename(), ex);
        }

        // ── 2. Auth + Hops + IOCs (re-open stream)
        try (var is2 = file.getInputStream()) {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage msg = new MimeMessage(session, is2);

            String[] received = msg.getHeader("Received");
            analysis.setSpfResult(spfChecker.check(firstHeader(msg, "Received-SPF")));
            analysis.setDkimResult(dkimChecker.check(firstHeader(msg, "DKIM-Signature")));
            analysis.setDmarcResult(dmarcChecker.check(firstHeader(msg, "Authentication-Results")));

            analysis.setHopEvents(hopChainBuilder.build(received, analysis));
            analysis.setIocs(extractIocs(analysis, msg));

        } catch (Exception ex) {
            log.warn("AnalysisService: auth/hop extraction partial failure — {}", ex.getMessage());
        }

        // ── 3. Risk scoring
        RiskReport report = buildRiskReport(analysis);
        analysis.setRiskReport(report);

        // ── 4. Fingerprint → ML → campaign match (degrades gracefully)
        campaignMatchService.processAndMatch(analysis);

        // ── 5. Recalculate overall score using ML result
        recalcWithMl(report);

        // ── 6. Persist
        EmailAnalysis saved = analysisRepository.save(analysis);
        log.info("AnalysisService: saved id={} risk={} cluster={}",
                saved.getId(), report.getRiskLevel(), report.getClusterId());
        return saved;
    }

    // ── Risk Scoring ──────────────────────────────────────

    private RiskReport buildRiskReport(EmailAnalysis a) {
        RiskBreakdown bd = RiskBreakdown.builder()
                .authenticationScore(scoreAuth(a))
                .senderScore(scoreSender(a))
                .domainScore(scoreDomain(a))
                .contentScore(scoreContent(a))
                .networkScore(0.0)  // updated after geo in CampaignMatchService
                .mlScore(0.0)       // updated after ML
                .build();

        double overall = weighted(bd);
        return RiskReport.builder()
                .riskBreakdown(bd)
                .overallRiskScore(overall)
                .riskLevel(RiskReport.calculateRiskLevel(overall))
                .generatedAt(Instant.now())
                .build();
    }

    private double scoreAuth(EmailAnalysis a) {
        double s = 0;
        if (!"PASS".equals(a.getSpfResult()))   s += 30;
        if (!"PASS".equals(a.getDkimResult()))  s += 40;
        if (!"PASS".equals(a.getDmarcResult())) s += 30;
        return Math.min(s, 100);
    }

    private double scoreSender(EmailAnalysis a) {
        double s = 0;
        if (a.getSenderIp() == null)    s += 35;
        if (a.getFromAddress() == null) s += 20;
        return Math.min(s, 100);
    }

    private double scoreDomain(EmailAnalysis a) {
        double s = 0;
        if (a.getSenderDomain() == null) s += 20;
        if (a.getDkimDomain() != null && a.getSenderDomain() != null
                && !a.getDkimDomain().equalsIgnoreCase(a.getSenderDomain())) {
            s += 35;  // domain mismatch = suspicious
        }
        return Math.min(s, 100);
    }

    private double scoreContent(EmailAnalysis a) {
        long urlCount = a.getIocs() == null ? 0 :
                a.getIocs().stream().filter(i -> "URL".equals(i.getType())).count();
        return Math.min(urlCount * 10, 100);
    }

    private double weighted(RiskBreakdown b) {
        return b.getAuthenticationScore() * 0.30
             + b.getSenderScore()         * 0.20
             + b.getDomainScore()         * 0.15
             + b.getContentScore()        * 0.15
             + b.getNetworkScore()        * 0.10
             + b.getMlScore()             * 0.10;
    }

    private void recalcWithMl(RiskReport r) {
        if (r == null || r.getClusteringConfidence() == null) return;
        RiskBreakdown b = r.getRiskBreakdown();
        if (b == null) return;
        b.setMlScore(r.getClusteringConfidence() * 100);
        double newScore = weighted(b);
        r.setOverallRiskScore(newScore);
        r.setRiskLevel(RiskReport.calculateRiskLevel(newScore));
    }

    // ── IOC Extraction ────────────────────────────────────

    private List<Ioc> extractIocs(EmailAnalysis a, MimeMessage msg) {
        List<Ioc> iocs = new ArrayList<>();
        try {
            addIoc(iocs, "IP",          a.getSenderIp(),      "Received header", a);
            addIoc(iocs, "DOMAIN",      a.getSenderDomain(),  "From header",     a);
            addIoc(iocs, "EMAIL",       a.getFromAddress(),   "From header",     a);
            addIoc(iocs, "DKIM_DOMAIN", a.getDkimDomain(),   "DKIM-Signature",  a);

            Object content = msg.getContent();
            if (content instanceof String body) {
                urlExtractor.extractUrls(body).forEach(url ->
                        addIoc(iocs, "URL", url, "Email body", a));
            }
        } catch (Exception ex) {
            log.warn("AnalysisService: IOC extraction partial — {}", ex.getMessage());
        }
        return iocs;
    }

    private void addIoc(List<Ioc> list, String type, String value, String source, EmailAnalysis a) {
        if (value != null && !value.isBlank())
            list.add(Ioc.builder().type(type).value(value.trim()).source(source).emailAnalysis(a).build());
    }

    private String firstHeader(MimeMessage msg, String name) {
        try {
            String[] h = msg.getHeader(name);
            return (h != null && h.length > 0) ? h[0] : null;
        } catch (Exception e) { return null; }
    }
}