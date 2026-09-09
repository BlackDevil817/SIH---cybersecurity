package com.sih26106.emailintel.report;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sih26106.emailintel.ioc.IocFormatter;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.RiskBreakdown;
import com.sih26106.emailintel.model.RiskReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfReportGenerator {

    private final IocFormatter iocFormatter;

    private static final DeviceRgb COL_HEADER   = new DeviceRgb(44, 62, 80);
    private static final DeviceRgb COL_CRITICAL  = new DeviceRgb(192, 57, 43);
    private static final DeviceRgb COL_HIGH      = new DeviceRgb(230, 126, 34);
    private static final DeviceRgb COL_MEDIUM    = new DeviceRgb(243, 156, 18);
    private static final DeviceRgb COL_LOW       = new DeviceRgb(39, 174, 96);
    private static final DeviceRgb COL_SECTION   = new DeviceRgb(52, 73, 94);

    public byte[] generate(EmailAnalysis analysis) {
        if (analysis == null) { log.warn("PdfReportGenerator: null analysis"); return new byte[0]; }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdf)) {

            // Title
            doc.add(new Paragraph("Email Intelligence Risk Report")
                    .setFontSize(20).setFontColor(COL_HEADER).setBold()
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Phishing Campaign Analysis Platform")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
            gap(doc);

            RiskReport report = analysis.getRiskReport();
            addMeta(doc, analysis, report);    gap(doc);
            addEmailInfo(doc, analysis);        gap(doc);
            addRiskBreakdown(doc, report);      gap(doc);
            addIocs(doc, analysis);             gap(doc);
            addFingerprint(doc, report);        gap(doc);
            addMl(doc, report);                 gap(doc);
            addConclusion(doc, report);

        } catch (Exception ex) {
            log.error("PdfReportGenerator: failed", ex);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    // â”€â”€ Sections â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void addMeta(Document doc, EmailAnalysis a, RiskReport r) {
        title(doc, "Report Summary");
        Table t = table2();
        String level = r != null && r.getRiskLevel() != null ? r.getRiskLevel() : "UNKNOWN";
        row(t, "Report ID",      str(a.getId()));
        row(t, "Analyzed At",    str(a.getAnalyzedAt()));
        row(t, "Overall Score",  r != null ? String.format("%.1f / 100", r.getOverallRiskScore()) : "N/A");
        t.addCell(hCell("Risk Level"));
        t.addCell(new Cell().add(new Paragraph(level)
                .setFontColor(ColorConstants.WHITE).setBackgroundColor(riskColor(level)).setBold().setFontSize(10)));
        doc.add(t);
    }

    private void addEmailInfo(Document doc, EmailAnalysis a) {
        title(doc, "Email Information");
        Table t = table2();
        row(t, "File",          a.getFileName());
        row(t, "Subject",       a.getSubject());
        row(t, "From",          a.getFromAddress());
        row(t, "Sender Domain", a.getSenderDomain());
        row(t, "Sender IP",     a.getSenderIp());
        row(t, "DKIM Domain",   a.getDkimDomain());
        row(t, "SPF",           a.getSpfResult());
        row(t, "DKIM",          a.getDkimResult());
        row(t, "DMARC",         a.getDmarcResult());
        doc.add(t);
    }

    private void addRiskBreakdown(Document doc, RiskReport r) {
        title(doc, "Risk Breakdown");
        if (r == null || r.getRiskBreakdown() == null) {
            doc.add(new Paragraph("No breakdown available.").setFontColor(ColorConstants.GRAY)); return;
        }
        RiskBreakdown b = r.getRiskBreakdown();
        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 25, 25})).useAllAvailableWidth();
        t.addHeaderCell(hCell("Factor"));
        t.addHeaderCell(hCell("Score (0-100)"));
        t.addHeaderCell(hCell("Severity"));
        riskRow(t, "Authentication (SPF/DKIM/DMARC)", b.getAuthenticationScore());
        riskRow(t, "Sender Reputation",               b.getSenderScore());
        riskRow(t, "Domain Analysis",                 b.getDomainScore());
        riskRow(t, "Network / IP",                    b.getNetworkScore());
        riskRow(t, "Content / URLs",                  b.getContentScore());
        riskRow(t, "ML Clustering",                   b.getMlScore());
        doc.add(t);
    }

    private void addIocs(Document doc, EmailAnalysis a) {
        title(doc, "Indicators of Compromise (IOC)");
        if (a.getIocs() == null || a.getIocs().isEmpty()) {
            doc.add(new Paragraph("No IOCs identified.").setFontColor(ColorConstants.GRAY)); return;
        }
        Map<String, List<String>> grouped = iocFormatter.formatGrouped(a.getIocs());
        grouped.forEach((type, vals) -> {
            doc.add(new Paragraph(type).setBold().setFontColor(COL_SECTION).setFontSize(11));
            vals.forEach(v -> doc.add(new Paragraph("  \u2022 " + v).setFontSize(10)));
        });
    }

    private void addFingerprint(Document doc, RiskReport r) {
        title(doc, "Sender Fingerprint");
        if (r == null) { doc.add(new Paragraph("No fingerprint data.").setFontColor(ColorConstants.GRAY)); return; }
        Table t = table2();
        row(t, "ASN",           r.getSenderAsn());
        row(t, "IP Range",      r.getSenderIpRange());
        row(t, "DKIM Domain",   r.getDkimDomain());
        row(t, "Sender Domain", r.getSenderDomain());
        doc.add(t);
    }

    private void addMl(Document doc, RiskReport r) {
        title(doc, "ML Clustering Analysis");
        if (r == null || r.getClusterId() == null) {
            doc.add(new Paragraph("ML results unavailable.").setFontColor(ColorConstants.GRAY).setItalic()); return;
        }
        Table t = table2();
        row(t, "Cluster ID",          r.getClusterId());
        row(t, "Label",               r.getClusterLabel());
        row(t, "Confidence",          pct(r.getClusteringConfidence()));
        row(t, "Campaign Similarity", pct(r.getCampaignSimilarity()));
        doc.add(t);
    }

    private void addConclusion(Document doc, RiskReport r) {
        title(doc, "Conclusion");
        String level = (r != null && r.getRiskLevel() != null) ? r.getRiskLevel() : "UNKNOWN";
        String msg = switch (level) {
            case "CRITICAL" -> "Critical risk â€” immediate investigation required.";
            case "HIGH"     -> "High risk â€” investigate before acting on this email.";
            case "MEDIUM"   -> "Medium risk â€” review carefully.";
            case "LOW"      -> "Low risk â€” standard precautions apply.";
            default         -> "Risk level could not be determined.";
        };
        doc.add(new Paragraph(msg).setFontSize(11));
        doc.add(new Paragraph("\nGenerated by EmailIntel")
                .setFontSize(9).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void title(Document doc, String t) {
        doc.add(new Paragraph(t).setFontSize(13).setBold()
                .setFontColor(COL_SECTION).setMarginTop(12).setMarginBottom(4));
    }
    private void gap(Document doc) { doc.add(new Paragraph(" ")); }

    private Table table2() {
        return new Table(UnitValue.createPercentArray(new float[]{35, 65})).useAllAvailableWidth();
    }
    private void row(Table t, String k, String v) {
        t.addCell(hCell(k));
        t.addCell(new Cell().add(new Paragraph(v != null ? v : "N/A").setFontSize(10)));
    }
    private void riskRow(Table t, String factor, double score) {
        String sev = RiskReport.calculateRiskLevel(score);
        t.addCell(new Cell().add(new Paragraph(factor).setFontSize(10)));
        t.addCell(new Cell().add(new Paragraph(String.format("%.1f", score)).setFontSize(10)));
        t.addCell(new Cell().add(new Paragraph(sev)
                .setFontColor(ColorConstants.WHITE).setBackgroundColor(riskColor(sev)).setFontSize(9)));
    }
    private Cell hCell(String text) {
        return new Cell().add(new Paragraph(text).setFontColor(ColorConstants.WHITE)
                .setBold().setFontSize(10)).setBackgroundColor(COL_HEADER);
    }
    private DeviceRgb riskColor(String lvl) {
        return switch (lvl != null ? lvl : "") {
            case "CRITICAL" -> COL_CRITICAL; case "HIGH" -> COL_HIGH;
            case "MEDIUM"   -> COL_MEDIUM;   default     -> COL_LOW;
        };
    }
    private String str(Object o)  { return o == null ? "N/A" : o.toString(); }
    private String pct(Double v)  { return v != null ? String.format("%.1f%%", v * 100) : "N/A"; }
}
