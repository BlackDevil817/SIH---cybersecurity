package com.sih26106.emailintel.mlclient.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClusterResponse {
    private String clusterId;
    private Double confidence;
    private String label;
    private List<String> similarCampaigns;
    private boolean available;
    private String errorMessage;

    public static ClusterResponse unavailable(String reason) {
        return ClusterResponse.builder()
                .available(false)
                .errorMessage(reason)
                .build();
    }
}