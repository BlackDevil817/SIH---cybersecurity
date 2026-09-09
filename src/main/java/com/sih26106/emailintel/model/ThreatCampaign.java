package com.sih26106.emailintel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "threat_campaigns")
public class ThreatCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String clusterId;
    private String label;
    private Double confidence;
    private Double campaignSimilarity;
    private Instant firstSeen;
    private Instant lastSeen;

    @JsonIgnore
    @OneToMany(mappedBy = "threatCampaign")
    @Builder.Default
    private List<EmailAnalysis> emailAnalyses = new ArrayList<>();
}
