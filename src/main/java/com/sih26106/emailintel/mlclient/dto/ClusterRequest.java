package com.sih26106.emailintel.mlclient.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClusterRequest {
    private String senderDomain;
    private String replyToDomain;
    private String returnPathDomain;
    private String dkimDomain;
    private String originatingIp;
    private String asn;
    private String ipRange;
    private String organization;
    private List<String> urlDomains;
    private Map<String, Object> features;
}