package com.sih26106.emailintel.mlclient;

import com.sih26106.emailintel.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MLServiceClient {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    public boolean isAvailable() {
        try {
            restTemplate.getForObject(appConfig.getMlServiceUrl() + "/health", String.class);
            return true;
        } catch (Exception ex) {
            log.debug("MLServiceClient: health check failed — {}", ex.getMessage());
            return false;
        }
    }
}