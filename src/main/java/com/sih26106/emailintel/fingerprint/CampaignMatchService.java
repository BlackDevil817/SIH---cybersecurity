package com.sih26106.emailintel.fingerprint;

import com.sih26106.emailintel.mlclient.ClusteringClient;
import com.sih26106.emailintel.mlclient.dto.ClusterResponse;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.RiskReport;
import com.sih26106.emailintel.model.ThreatCampaign;
import com.sih26106.emailintel.repository.ThreatCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignMatchService {

    private final SenderInfraExtractor infraExtractor;
    private final ClusteringClient clusteringClient;
    private final ThreatCampaignRepository campaignRepository;

    /**
     * Full pipeline: fingerprint → ML → attach results → persist campaign.
     * Degrades gracefully — never throws.
     */
    @Transactional
    public void processAndMatch(EmailAnalysis analysis) {
        if (analysis == null) return;

        // ── 1. Extract fingerprint
        SenderFingerprint fingerprint;
        try {
            fingerprint = infraExtractor.extract(analysis);
        } catch (Exception ex) {
            log.error("CampaignMatchService: fingerprint extraction failed", ex);
            return;
        }

        // ── 2. Store fingerprint summary in RiskReport for reporting
        RiskReport report = analysis.getRiskReport();
        if (report != null) {
            report.setSenderAsn(fingerprint.getAsn());
            report.setSenderIpRange(fingerprint.getIpRange());
            report.setDkimDomain(fingerprint.getDkimDomain());
            report.setSenderDomain(fingerprint.getSenderDomain());
        }

        // ── 3. Call ML clustering service
        ClusterResponse cluster = clusteringClient.cluster(fingerprint);

        // ── 4. Attach ML results to RiskReport
        if (report != null && cluster.isAvailable()) {
            report.setClusterId(cluster.getClusterId());
            report.setClusteringConfidence(cluster.getConfidence());
            report.setClusterLabel(cluster.getLabel());
            report.setCampaignSimilarity(
                    cluster.getSimilarCampaigns() != null
                            && !cluster.getSimilarCampaigns().isEmpty() ? 1.0 : 0.0);
            log.info("CampaignMatchService: ML cluster={} confidence={}",
                    cluster.getClusterId(), cluster.getConfidence());
        } else {
            log.warn("CampaignMatchService: ML unavailable — {}", cluster.getErrorMessage());
        }

        // ── 5. Match or create ThreatCampaign
        if (cluster.isAvailable() && cluster.getClusterId() != null) {
            ThreatCampaign campaign = campaignRepository
                    .findByClusterId(cluster.getClusterId())
                    .orElseGet(() -> ThreatCampaign.builder()
                            .clusterId(cluster.getClusterId())
                            .label(cluster.getLabel())
                            .confidence(cluster.getConfidence())
                            .firstSeen(Instant.now())
                            .build());
            campaign.setLastSeen(Instant.now());
            analysis.setThreatCampaign(campaignRepository.save(campaign));
        }
    }
}