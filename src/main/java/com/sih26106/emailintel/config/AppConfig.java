package com.sih26106.emailintel.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class AppConfig {

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${ml.service.timeout.ms:5000}")
    private int mlTimeoutMs;

    @Value("${geo.api.url:http://ip-api.com/json}")
    private String geoApiUrl;

    @Value("${geo.api.key:}")
    private String geoApiKey;
}