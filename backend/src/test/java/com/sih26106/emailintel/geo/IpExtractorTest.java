package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.model.IpClassification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpExtractorTest {

    private final IpExtractor extractor = new IpExtractor();

    @Test
    void classifiesPublicIpv4() {
        assertEquals(IpClassification.PUBLIC, extractor.classify("8.8.8.8"));
    }

    @Test
    void classifiesPrivateIpv4() {
        assertEquals(IpClassification.PRIVATE, extractor.classify("192.168.1.5"));
        assertEquals(IpClassification.PRIVATE, extractor.classify("10.0.0.1"));
        assertEquals(IpClassification.PRIVATE, extractor.classify("172.16.5.5"));
    }

    @Test
    void classifiesLoopback() {
        assertEquals(IpClassification.LOOPBACK, extractor.classify("127.0.0.1"));
        assertEquals(IpClassification.LOOPBACK, extractor.classify("::1"));
    }

    @Test
    void classifiesPublicIpv6() {
        assertEquals(IpClassification.PUBLIC, extractor.classify("2001:4860:4860::8888"));
    }

    @Test
    void classifiesLinkLocalIpv6() {
        assertEquals(IpClassification.LINK_LOCAL, extractor.classify("fe80::1"));
    }

    @Test
    void classifiesInvalidIpAsInvalidWithoutThrowingOrHanging() {
        assertEquals(IpClassification.INVALID, extractor.classify("999.999.999.999"));
        assertEquals(IpClassification.INVALID, extractor.classify("not-an-ip-at-all"));
    }

    @Test
    void classifiesNullOrBlankAsUnknown() {
        assertEquals(IpClassification.UNKNOWN, extractor.classify(null));
        assertEquals(IpClassification.UNKNOWN, extractor.classify("  "));
    }

    @Test
    void extractsFirstIpFromReceivedHeaderStyleText() {
        String text = "mail.example.com (mail.example.com [203.0.113.10])";
        assertEquals("203.0.113.10", extractor.extractFirstIp(text).orElseThrow());
    }

    @Test
    void extractsMultipleDistinctIpsInOrderOfAppearance() {
        String text = "hop1 [203.0.113.10] then hop2 [198.51.100.20] then hop1 again [203.0.113.10]";
        List<String> ips = extractor.extractAllIps(text);
        assertEquals(List.of("203.0.113.10", "198.51.100.20"), ips);
    }

    @Test
    void doesNotFalsePositiveOnTimestampsOrIdsOrPortNumbers() {
        String text = "id abc123 for <user@host.com>; Tue, 1 Sep 2026 10:15:20 +0000 port 8080";
        assertTrue(extractor.extractAllIps(text).isEmpty());
    }

    @Test
    void returnsEmptyForBlankOrNullInput() {
        assertTrue(extractor.extractAllIps(null).isEmpty());
        assertTrue(extractor.extractAllIps("").isEmpty());
        assertTrue(extractor.extractFirstIp("no ip here").isEmpty());
    }
}
