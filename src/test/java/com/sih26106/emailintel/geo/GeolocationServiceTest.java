package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.config.AppConfig;
import com.sih26106.emailintel.geo.dto.GeoInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)

class GeolocationServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock AppConfig appConfig;
    @InjectMocks GeolocationService service;

    @BeforeEach void setup() { when(appConfig.getGeoApiUrl()).thenReturn("http://ip-api.com/json"); }

    @Test void testValidPublicIp() {
        GeoInfo geo = GeoInfo.builder().status("success").country("US").city("NYC").build();
        when(restTemplate.getForObject(anyString(), eq(GeoInfo.class))).thenReturn(geo);
        GeoInfo r = service.geolocate("8.8.8.8");
        assertThat(r.isValid()).isTrue();
        assertThat(r.isPrivateAddress()).isFalse();
        assertThat(r.getCountry()).isEqualTo("US");
    }

    @Test void testPrivateIp_10x() {
        GeoInfo r = service.geolocate("10.0.0.1");
        assertThat(r.isPrivateAddress()).isTrue();
        assertThat(r.isValid()).isTrue();
        verify(restTemplate, never()).getForObject(any(), any());
    }

    @Test void testPrivateIp_192168() {
        assertThat(service.geolocate("192.168.1.1").isPrivateAddress()).isTrue();
    }

    @Test void testLoopback() {
        assertThat(service.geolocate("127.0.0.1").isPrivateAddress()).isTrue();
    }

    @Test void testNullIp() { assertThat(service.geolocate(null).isValid()).isFalse(); }
    @Test void testBlankIp() { assertThat(service.geolocate("  ").isValid()).isFalse(); }
    @Test void testInvalidFormat() { assertThat(service.geolocate("not-an-ip").isValid()).isFalse(); }

    @Test void testProviderDown() {
        when(restTemplate.getForObject(anyString(), eq(GeoInfo.class)))
                .thenThrow(new ResourceAccessException("refused"));
        assertThat(service.geolocate("8.8.8.8").isValid()).isFalse();
    }

    @Test void testApiReturnsFail() {
        when(restTemplate.getForObject(anyString(), eq(GeoInfo.class)))
                .thenReturn(GeoInfo.builder().status("fail").build());
        assertThat(service.geolocate("1.2.3.4").isValid()).isFalse();
    }

    @Test void testNullApiResponse() {
        when(restTemplate.getForObject(anyString(), eq(GeoInfo.class))).thenReturn(null);
        assertThat(service.geolocate("1.2.3.4").isValid()).isFalse();
    }
}
