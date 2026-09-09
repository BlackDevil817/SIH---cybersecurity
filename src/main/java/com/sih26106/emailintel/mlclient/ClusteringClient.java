package com.sih26106.emailintel.mlclient;

import com.sih26106.emailintel.config.AppConfig;
import com.sih26106.emailintel.fingerprint.SenderFingerprint;
import com.sih26106.emailintel.mlclient.dto.ClusterRequest;
import com.sih26106.emailintel.mlclient.dto.ClusterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClusteringClient {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    private static final String CLUSTER_PATH = "/api/ml/cluster";

    /**
     * Send fingerprint to ML service — never throws.
     * Returns ClusterResponse.unavailable() on any failure.
     */
    public ClusterResponse cluster(SenderFingerprint fp) {
        if (fp == null) {
            return ClusterResponse.unavailable("No fingerprint provided");
        }

        String url = appConfig.getMlServiceUrl() + CLUSTER_PATH;
        ClusterRequest request = toRequest(fp);

        try {
            log.debug("ClusteringClient: POST {} [domain={} asn={}]",
                    url, request.getSenderDomain(), request.getAsn());

            ClusterResponse response = restTemplate.postForObject(url, request, ClusterResponse.class);

            if (response == null) {
                return ClusterResponse.unavailable("Empty response from ML service");
            }
            response.setAvailable(true);
            log.info("ClusteringClient: cluster={} confidence={} label={}",
                    response.getClusterId(), response.getConfidence(), response.getLabel());
            return response;

        } catch (ResourceAccessException ex) {
            log.warn("ClusteringClient: ML service unreachable at {}", url);
            return ClusterResponse.unavailable("ML service unreachable");
        } catch (RestClientException ex) {
            log.warn("ClusteringClient: REST error — {}", ex.getMessage());
            return ClusterResponse.unavailable("ML REST error: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("ClusteringClient: unexpected error", ex);
            return ClusterResponse.unavailable("Unexpected ML client error");
        }
    }

    private ClusterRequest toRequest(SenderFingerprint fp) {
        return ClusterRequest.builder()
                .senderDomain(fp.getSenderDomain())
                .replyToDomain(fp.getReplyToDomain())
                .returnPathDomain(fp.getReturnPathDomain())
                .dkimDomain(fp.getDkimDomain())
                .originatingIp(fp.getOriginatingIp())
                .asn(fp.getAsn())
                .ipRange(fp.getIpRange())
                .organization(fp.getAsnOrganization())
                .urlDomains(fp.getUrlDomains())
                .features(fp.getFeatures())
                .build();
    }
}