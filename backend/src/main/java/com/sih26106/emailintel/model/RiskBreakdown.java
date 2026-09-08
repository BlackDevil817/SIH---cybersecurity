package com.sih26106.emailintel.model;

import java.util.Map;

/**
 * PHASE 1 STUB - out of scope for this backend module.
 * Reserved for the ML team's per-factor risk breakdown (e.g. auth failures,
 * geo anomalies, hop anomalies each contributing a weighted sub-score).
 */
public class RiskBreakdown {
    private Map<String, Double> factorScores;

    public Map<String, Double> getFactorScores() {
        return factorScores;
    }

    public void setFactorScores(Map<String, Double> factorScores) {
        this.factorScores = factorScores;
    }
}
