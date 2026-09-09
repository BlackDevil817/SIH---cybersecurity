package com.sih26106.emailintel.repository;

import com.sih26106.emailintel.model.ThreatCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ThreatCampaignRepository extends JpaRepository<ThreatCampaign, Long> {
    Optional<ThreatCampaign> findByClusterId(String clusterId);
}