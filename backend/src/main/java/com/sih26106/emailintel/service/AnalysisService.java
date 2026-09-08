package com.sih26106.emailintel.service;

import com.sih26106.emailintel.model.EmailAnalysis;

/**
 * Runs the forensic analysis pipeline (currently: SPF/DKIM/DMARC) against an already-uploaded
 * and parsed email, and provides read access to stored analyses.
 *
 * Phase 3 will extend the pipeline invoked by analyze() with Received-header parsing,
 * IP extraction, geolocation and hop-chain building - this interface's method signatures
 * are not expected to change when that happens.
 */
public interface AnalysisService {

    /**
     * Runs SPF/DKIM/DMARC checks against the stored EmailAnalysis's headers, populates its
     * authentication summary, marks it ANALYZED, and persists the update.
     *
     * @throws com.sih26106.emailintel.exception.AnalysisNotFoundException if analysisId is unknown
     */
    EmailAnalysis analyze(String analysisId);

    /**
     * @throws com.sih26106.emailintel.exception.AnalysisNotFoundException if analysisId is unknown
     */
    EmailAnalysis getAnalysis(String analysisId);
}
