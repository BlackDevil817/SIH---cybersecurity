package com.sih26106.emailintel.report;

import com.sih26106.emailintel.ioc.IocFormatter;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.RiskBreakdown;
import com.sih26106.emailintel.model.RiskReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlReportGenerator {

    private final IocFormatter iocFormatter;

    public String generate(EmailAnalysis analysis) {
        if (analysis == null)
            return "<html><body><h1>No analysis data available</h1></body></html>";

        RiskReport report = analysis.getRiskReport();
        StringBuilder html = new StringBuilder();
        html.append(head());
        html.append("<body><div class='container'>\n");
        html.append(sectionHeader(analysis, report));
        html.append(sectionEmail(analysis));
        html.append(sectionRisk(report));
        html.append(sectionIoc(analysis));
        html.append(sectionFingerprint(report));
        html.append(sectionMl(report));
        html.append(sectionConclusion(report));
        html.append("</div></body></html>");

        log.debug("HtmlReportGenerator: generated for analysis {}", analysis.getId());
        return html.toString();
    }

    // ── Sections ─────────────────────────────────────────

    private String sectionHeader(EmailAnalysis a, RiskReport r) {
        String level = r != null && r.getRiskLevel() != null ? r.getRiskLevel() : "UNKNOWN";
        String score = r != null ? String.format("%.1f", r.getOverallRiskScore()) : "N/A";
        return """
            <div class='section'>
              <h1>&#128267; Email Intelligence Risk Report</h1>
              <table>
                <tr><th>Report ID</th><td>%s</td></tr>
                <tr><th>Generated At</th><td>%s</td></tr>
                <tr><th>Overall Risk Score</th><td><strong>%s / 100</strong></td></tr>
                <tr><th>Risk Level</th><td><span class='badge risk-%s'>%s</span></td></tr>
              </table>
            </div>
            """.formatted(
                e(str(a.getId())), e(str(a.getAnalyzedAt())),
                e(score), e(level.toLowerCase()), e(level));
    }

    private String sectionEmail(EmailAnalysis a) {
        return """
            <div class='section'>
              <h2>&#128231; Email Information</h2>
              <table>
                <tr><th>File</th><td>%s</td></tr>
                <tr><th>Subject</th><td>%s</td></tr>
                <tr><th>From</th><td>%s</td></tr>
                <tr><th>Sender Domain</th><td>%s</td></tr>
                <tr><th>Sender IP</th><td>%s</td></tr>
                <tr><th>DKIM Domain</th><td>%s</td></tr>
                <tr><th>SPF</th><td>%s</td></tr>
                <tr><th>DKIM</th><td>%s</td></tr>
                <tr><th>DMARC</th><td>%s</td></tr>
              </table>
            </div>
            """.formatted(
                e(a.getFileName()), e(a.getSubject()), e(a.getFromAddress()),
                e(a.getSenderDomain()), e(a.getSenderIp()), e(a.getDkimDomain()),
                e(a.getSpfResult()), e(a.getDkimResult()), e(a.getDmarcResult()));
    }

    private String sectionRisk(RiskReport r) {
        if (r == null || r.getRiskBreakdown() == null)
            return "<div class='section'><h2>Risk Analysis</h2><p>No data.</p></div>";
        RiskBreakdown b = r.getRiskBreakdown();
        return """
            <div class='section'>
              <h2>&#9888;&#65039; Risk Breakdown</h2>
              <table>
                <tr><th>Factor</th><th>Score</th><th>Bar</th></tr>
                %s%s%s%s%s%s
              </table>
            </div>
            """.formatted(
                riskRow("Authentication (SPF/DKIM/DMARC)", b.getAuthenticationScore()),
                riskRow("Sender Reputation",               b.getSenderScore()),
                riskRow("Domain Analysis",                 b.getDomainScore()),
                riskRow("Network / IP",                    b.getNetworkScore()),
                riskRow("Content / URLs",                  b.getContentScore()),
                riskRow("ML Clustering",                   b.getMlScore()));
    }

    private String sectionIoc(EmailAnalysis a) {
        if (a.getIocs() == null || a.getIocs().isEmpty())
            return "<div class='section'><h2>IOCs</h2><p>No indicators found.</p></div>";
        Map<String, List<String>> grouped = iocFormatter.formatGrouped(a.getIocs());
        StringBuilder sb = new StringBuilder("<div class='section'><h2>&#128203; Indicators of Compromise</h2>\n");
        grouped.forEach((type, vals) -> {
            sb.append("<h3>").append(e(type)).append("</h3><p>");
            vals.forEach(v -> sb.append("<span class='pill'>").append(e(v)).append("</span> "));
            sb.append("</p>\n");
        });
        return sb.append("</div>\n").toString();
    }

    private String sectionFingerprint(RiskReport r) {
        if (r == null) return "";
        return """
            <div class='section'>
              <h2>&#128270; Sender Fingerprint</h2>
              <table>
                <tr><th>ASN</th><td>%s</td></tr>
                <tr><th>IP Range</th><td>%s</td></tr>
                <tr><th>DKIM Domain</th><td>%s</td></tr>
                <tr><th>Sender Domain</th><td>%s</td></tr>
              </table>
            </div>
            """.formatted(e(r.getSenderAsn()), e(r.getSenderIpRange()),
                          e(r.getDkimDomain()), e(r.getSenderDomain()));
    }

    private String sectionMl(RiskReport r) {
        if (r == null) return "";
        if (r.getClusterId() == null)
            return "<div class='section'><h2>&#129302; ML Analysis</h2><p class='muted'>ML results unavailable.</p></div>";
        return """
            <div class='section'>
              <h2>&#129302; ML Clustering Analysis</h2>
              <table>
                <tr><th>Cluster ID</th><td>%s</td></tr>
                <tr><th>Label</th><td>%s</td></tr>
                <tr><th>Confidence</th><td>%.1f%%</td></tr>
                <tr><th>Campaign Similarity</th><td>%.1f%%</td></tr>
              </table>
            </div>
            """.formatted(e(r.getClusterId()), e(r.getClusterLabel()),
                pct(r.getClusteringConfidence()), pct(r.getCampaignSimilarity()));
    }

    private String sectionConclusion(RiskReport r) {
        String level = (r != null && r.getRiskLevel() != null) ? r.getRiskLevel() : "UNKNOWN";
        String msg = switch (level) {
            case "CRITICAL" -> "Critical risk. Immediate investigation required.";
            case "HIGH"     -> "High risk. Treat with caution and investigate.";
            case "MEDIUM"   -> "Medium risk. Review carefully before acting.";
            case "LOW"      -> "Low risk. Standard precautions apply.";
            default         -> "Risk level undetermined.";
        };
        return "<div class='section'><h2>Conclusion</h2><p>" + e(msg) + "</p></div>" +
               "<div class='footer'>EmailIntel &mdash; Phishing Campaign Analysis</div>";
    }

    // ── Helpers ──────────────────────────────────────────

    private String riskRow(String label, double score) {
        int w = (int) Math.min(score * 2, 200);
        return "<tr><td>" + e(label) + "</td><td><strong>" + String.format("%.1f", score)
                + "</strong></td><td><div class='bar-bg'><div class='bar' style='width:" + w + "px'></div></div></td></tr>\n";
    }

    /** Escape HTML to prevent XSS */
    private String e(String v) {
        if (v == null) return "<em>N/A</em>";
        return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#x27;");
    }
    private String str(Object o) { return o == null ? null : o.toString(); }
    private double pct(Double v)  { return v != null ? v * 100 : 0.0; }

    private String head() {
        return """
            <!DOCTYPE html><html lang="en"><head>
            <meta charset="UTF-8"><title>EmailIntel Report</title>
            <style>
              body{font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:20px}
              .container{max-width:960px;margin:auto;background:#fff;padding:30px;border-radius:8px;box-shadow:0 2px 10px rgba(0,0,0,.1)}
              h1{color:#2c3e50;border-bottom:3px solid #e74c3c;padding-bottom:10px}
              h2{color:#34495e;border-left:4px solid #3498db;padding-left:10px;margin-top:28px}
              h3{color:#555}
              table{width:100%;border-collapse:collapse;margin:12px 0}
              th{background:#2c3e50;color:#fff;padding:9px;text-align:left;font-size:13px}
              td{padding:7px 10px;border-bottom:1px solid #ddd;font-size:13px}
              tr:nth-child(even){background:#f9f9f9}
              .badge{padding:3px 10px;border-radius:4px;font-weight:bold;color:#fff;font-size:12px}
              .risk-critical{background:#c0392b}.risk-high{background:#e67e22}
              .risk-medium{background:#f39c12}.risk-low{background:#27ae60}
              .bar-bg{background:#ecf0f1;border-radius:4px;height:16px;width:200px;display:inline-block}
              .bar{height:16px;border-radius:4px;background:#e74c3c}
              .pill{display:inline-block;background:#ecf0f1;border-radius:12px;padding:2px 9px;margin:2px;font-size:12px;font-family:monospace}
              .muted{color:#999;font-style:italic}
              .section{margin:18px 0}
              .footer{margin-top:36px;font-size:11px;color:#999;text-align:center}
            </style></head>
            """;
    }
}