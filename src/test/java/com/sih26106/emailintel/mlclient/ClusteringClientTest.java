package com.sih26106.emailintel.mlclient;

import com.sih26106.emailintel.config.AppConfig;
import com.sih26106.emailintel.fingerprint.SenderFingerprint;
import com.sih26106.emailintel.mlclient.dto.ClusterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusteringClientTest {

    @Mock RestTemplate restTemplate;
    @Mock AppConfig appConfig;
    @InjectMocks ClusteringClient client;

    @BeforeEach void setup() { when(appConfig.getMlServiceUrl()).thenReturn("http://localhost:8000"); }

    @Test void testSuccess() {
        ClusterResponse mock = ClusterResponse.builder()
                .clusterId("c-001").confidence(0.94).label("phishing").build();
        when(restTemplate.postForObject(anyString(), any(), eq(ClusterResponse.class))).thenReturn(mock);

        ClusterResponse r = client.cluster(SenderFingerprint.builder().senderDomain("evil.com").build());
        assertThat(r.isAvailable()).isTrue();
        assertThat(r.getClusterId()).isEqualTo("c-001");
        assertThat(r.getConfidence()).isEqualTo(0.94);
    }

    @Test void testMlDown() {
        when(restTemplate.postForObject(anyString(), any(), eq(ClusterResponse.class)))
                .thenThrow(new ResourceAccessException("refused"));
        ClusterResponse r = client.cluster(SenderFingerprint.builder().build());
        assertThat(r.isAvailable()).isFalse();
        assertThat(r.getErrorMessage()).isNotNull();
    }

    @Test void testNullFingerprint() {
        assertThat(client.cluster(null).isAvailable()).isFalse();
    }

    @Test void testNullMlResponse() {
        when(restTemplate.postForObject(anyString(), any(), eq(ClusterResponse.class))).thenReturn(null);
        assertThat(client.cluster(SenderFingerprint.builder().build()).isAvailable()).isFalse();
    }
}
