package com.sih26106.emailintel.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_analyses")
public class EmailAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String subject;
    private String fromAddress;
    private String toAddress;
    private String messageId;
    private String senderIp;
    private String spfResult;
    private String dkimResult;
    private String dmarcResult;
    private String senderDomain;
    private String dkimDomain;
    private String dkimSelector;
    private String replyToDomain;
    private String returnPathDomain;
    private Instant receivedAt;
    private Instant analyzedAt;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "risk_report_id")
    private RiskReport riskReport;

    @OneToMany(mappedBy = "emailAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HopEvent> hopEvents = new ArrayList<>();

    @OneToMany(mappedBy = "emailAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Ioc> iocs = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "threat_campaign_id")
    private ThreatCampaign threatCampaign;
}
