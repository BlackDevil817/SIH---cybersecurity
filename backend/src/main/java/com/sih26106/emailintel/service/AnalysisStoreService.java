package com.sih26106.emailintel.service;

import com.sih26106.emailintel.model.EmailAnalysis;

import java.util.Optional;

/**
 * Persistence abstraction for EmailAnalysis records.
 *
 * Phase 1-3 use an in-memory implementation (InMemoryAnalysisStoreService).
 * A JPA-backed implementation can be swapped in later (Section 17 of the spec)
 * without any change to controllers/services that depend on this interface.
 */
public interface AnalysisStoreService {

    EmailAnalysis save(EmailAnalysis analysis);

    Optional<EmailAnalysis> findById(String analysisId);

    EmailAnalysis update(EmailAnalysis analysis);
}
