package com.sih26106.emailintel.exception;

/**
 * Thrown when an analysisId does not correspond to any stored analysis.
 * Used starting from the /analyze and /emails/{id} endpoints (Phase 2+),
 * defined now so the exception contract is stable across phases.
 * Maps to HTTP 404.
 */
public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(String analysisId) {
        super("No analysis found for analysisId=" + analysisId);
    }
}
