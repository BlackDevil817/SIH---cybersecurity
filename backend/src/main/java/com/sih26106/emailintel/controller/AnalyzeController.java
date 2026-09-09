package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs forensic analysis on a previously uploaded email, and exposes its current state.
 *
 * Kept thin per the Controller -> Service layering used throughout this module; all
 * pipeline logic lives in AnalysisService.
 */
@RestController
@RequestMapping("/api/emails")
public class AnalyzeController {

    private final AnalysisService analysisService;

    public AnalyzeController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * POST /api/emails/{analysisId}/analyze
     *
     * Runs SPF/DKIM/DMARC checks against the previously parsed email and returns the
     * updated EmailAnalysis. 404 (via AnalysisNotFoundException) if analysisId is unknown.
     */
    @PostMapping("/{analysisId}/analyze")
    public ResponseEntity<EmailAnalysis> analyze(@PathVariable String analysisId) {
        return ResponseEntity.ok(analysisService.analyze(analysisId));
    }

    /**
     * GET /api/emails/{analysisId}
     *
     * Returns the current stored state of an analysis (PARSED before /analyze has been
     * called, ANALYZED afterward). 404 (via AnalysisNotFoundException) if unknown.
     */
    @GetMapping("/{analysisId}")
    public ResponseEntity<EmailAnalysis> get(@PathVariable String analysisId) {
        return ResponseEntity.ok(analysisService.getAnalysis(analysisId));
    }
}
