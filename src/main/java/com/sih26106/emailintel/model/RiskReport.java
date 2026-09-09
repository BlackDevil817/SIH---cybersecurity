package com.sih26106.emailintel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "risk_reports")
public class RiskReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double overallRiskScore;
    private String riskLevel;

    @Embedded
    private RiskBreakdown riskBreakdown;

    private String clusterId;
    private Double clusteringConfidence;
    private Double campaignSimilarity;
    private String clusterLabel;
    private String senderAsn;
    private String senderIpRange;
    private String dkimDomain;
    private String senderDomain;
    private Instant generatedAt;

    @JsonIgnore
    @OneToOne(mappedBy = "riskReport")
    private EmailAnalysis emailAnalysis;

    public static String calculateRiskLevel(double score) {
        if (score >= 75) return "CRITICAL";
        if (score >= 50) return "HIGH";
        if (score >= 25) return "MEDIUM";
        return "LOW";
    }
}
