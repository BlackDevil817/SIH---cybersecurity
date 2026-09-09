package com.sih26106.emailintel.service.impl;

import com.sih26106.emailintel.exception.AnalysisNotFoundException;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.service.AnalysisStoreService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AnalysisStoreService, suitable for the SIH demo/prototype.
 *
 * NOT durable across restarts and NOT suitable for multi-instance deployment - if that
 * becomes a requirement, implement AnalysisStoreService with Spring Data JPA
 * (see Section 17 of the architecture spec) and swap the @Service bean; no other
 * class needs to change since everything depends on the AnalysisStoreService interface.
 */
@Service
public class InMemoryAnalysisStoreService implements AnalysisStoreService {

    private final Map<String, EmailAnalysis> store = new ConcurrentHashMap<>();

    @Override
    public EmailAnalysis save(EmailAnalysis analysis) {
        analysis.setCreatedAt(Instant.now());
        analysis.setUpdatedAt(analysis.getCreatedAt());
        store.put(analysis.getAnalysisId(), analysis);
        return analysis;
    }

    @Override
    public Optional<EmailAnalysis> findById(String analysisId) {
        return Optional.ofNullable(store.get(analysisId));
    }

    @Override
    public EmailAnalysis update(EmailAnalysis analysis) {
        if (!store.containsKey(analysis.getAnalysisId())) {
            throw new AnalysisNotFoundException(analysis.getAnalysisId());
        }
        analysis.setUpdatedAt(Instant.now());
        store.put(analysis.getAnalysisId(), analysis);
        return analysis;
    }
}
