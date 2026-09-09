package com.sih26106.emailintel.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RiskBreakdown {

    private double authenticationScore;
    private double senderScore;
    private double domainScore;
    private double networkScore;
    private double contentScore;
    private double mlScore;

    private String authenticationReason;
    private String senderReason;
    private String domainReason;
    private String networkReason;
    private String contentReason;
    private String mlReason;
}
