package com.sih26106.emailintel.model;

/**
 * Lifecycle status of an EmailAnalysis record.
 */
public enum AnalysisStatus {
    UPLOADED,       // multipart accepted, not yet parsed
    PARSED,         // EmlParser/HeaderExtractor succeeded (Phase 1 end state)
    PARSE_FAILED,   // file was unprocessable as an email
    ANALYZING,      // /analyze pipeline in progress (Phase 2+)
    ANALYZED,       // auth checks + hop chain complete (Phase 2/3 end state)
    FAILED          // unrecoverable failure during analysis
}
