package com.sih26106.emailintel.repository;

import com.sih26106.emailintel.model.EmailAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, Long> {
    List<EmailAnalysis> findByThreatCampaignId(Long campaignId);
    List<EmailAnalysis> findBySenderIp(String senderIp);
}