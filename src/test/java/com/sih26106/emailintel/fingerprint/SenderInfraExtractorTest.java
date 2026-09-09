package com.sih26106.emailintel.fingerprint;

import com.sih26106.emailintel.geo.GeolocationService;
import com.sih26106.emailintel.geo.dto.GeoInfo;
import com.sih26106.emailintel.ioc.UrlExtractor;
import com.sih26106.emailintel.model.EmailAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SenderInfraExtractorTest {

    @Mock GeolocationService geo;
    @Mock UrlExtractor urlEx;
    @InjectMocks SenderInfraExtractor extractor;

    @Test void testFullExtraction() {
        when(geo.geolocate(anyString())).thenReturn(GeoInfo.builder()
                .valid(true).privateAddress(false).asn("AS15169 Google LLC").build());

        EmailAnalysis a = EmailAnalysis.builder()
                .senderIp("8.8.8.8").senderDomain("evil.com")
                .dkimDomain("evil.com").fromAddress("x@evil.com").build();

        SenderFingerprint fp = extractor.extract(a);
        assertThat(fp.getOriginatingIp()).isEqualTo("8.8.8.8");
        assertThat(fp.getAsn()).isEqualTo("AS15169");
        assertThat(fp.getAsnOrganization()).isEqualTo("Google LLC");
        assertThat(fp.getIpRange()).isEqualTo("8.8.8.0/24");
        assertThat(fp.getDkimDomain()).isEqualTo("evil.com");
    }

    @Test void testNullAnalysis() {
        assertThat(extractor.extract(null)).isNotNull();
    }

    @Test void testMissingIp() {
        SenderFingerprint fp = extractor.extract(EmailAnalysis.builder().senderDomain("x.com").build());
        assertThat(fp.getAsn()).isNull();
    }

    @Test void testAsnParsing() {
        assertThat(extractor.extractAsnNumber("AS15169 Google LLC")).isEqualTo("AS15169");
        assertThat(extractor.extractAsnOrg("AS15169 Google LLC")).isEqualTo("Google LLC");
        assertThat(extractor.extractAsnNumber(null)).isNull();
    }

    @Test void testIpRange() {
        assertThat(extractor.estimateIpRange("192.168.1.100")).isEqualTo("192.168.1.0/24");
        assertThat(extractor.estimateIpRange("8.8.8.8")).isEqualTo("8.8.8.0/24");
        assertThat(extractor.estimateIpRange(null)).isNull();
    }
}