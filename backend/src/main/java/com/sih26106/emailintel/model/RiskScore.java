package com.sih26106.emailintel.model;

/**
 * PHASE 1 STUB - out of scope for this backend module.
 * Reserved for the ML team's risk-scoring output. Kept here only so the
 * package/class name referenced elsewhere in the architecture is stable;
 * this backend module does not populate or interpret it.
 */
public class RiskScore {
    private Double overallScore; // 0.0 - 100.0, set by ML module
    private String riskLevel;    // e.g. LOW/MEDIUM/HIGH/CRITICAL, set by ML module

    public Double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
