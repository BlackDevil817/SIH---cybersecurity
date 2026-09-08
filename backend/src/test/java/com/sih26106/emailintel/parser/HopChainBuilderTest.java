package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.geo.IpExtractor;
import com.sih26106.emailintel.geo.impl.MockGeolocationService;
import com.sih26106.emailintel.model.HopEvent;
import com.sih26106.emailintel.model.IpClassification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HopChainBuilderTest {

    private final HopChainBuilder builder = new HopChainBuilder(
            new ReceivedHeaderAnalyzer(), new IpExtractor(), new MockGeolocationService());

    @Test
    void ordersMultipleHopsChronologicallyDespiteNewestFirstRawOrder() {
        // Raw order (as stored in EmailHeaders.getReceived()) is newest-first: index 0 has the
        // LATER timestamp (10:15), index 1 has the EARLIER timestamp (10:10).
        List<String> received = List.of(
                "from mail.example.com (mail.example.com [203.0.113.10]) "
                        + "by mx.target.com with ESMTPS id abc123; Tue, 1 Sep 2026 10:15:20 +0000",
                "from smtp.example.net (smtp.example.net [198.51.100.20]) "
                        + "by mail.example.com with ESMTP id def456; Tue, 1 Sep 2026 10:10:00 +0000");

        List<HopEvent> hops = builder.build(received);

        assertEquals(2, hops.size());
        // Chronological order: the OLDER (10:10, smtp.example.net) hop must come first.
        assertEquals(1, hops.get(0).getHopNumber());
        assertEquals("smtp.example.net", hops.get(0).getSourceHostname());
        assertEquals("198.51.100.20", hops.get(0).getSourceIp());

        assertEquals(2, hops.get(1).getHopNumber());
        assertEquals("mail.example.com", hops.get(1).getSourceHostname());
        assertEquals("203.0.113.10", hops.get(1).getSourceIp());

        assertTrue(hops.get(0).getTimestamp().isBefore(hops.get(1).getTimestamp()));
    }

    @Test
    void classifiesAndGeolocatesPublicIps() {
        List<String> received = List.of(
                "from mail.example.com (mail.example.com [203.0.113.10]) "
                        + "by mx.target.com with ESMTPS; Tue, 1 Sep 2026 10:15:20 +0000");

        List<HopEvent> hops = builder.build(received);
        HopEvent hop = hops.get(0);

        assertEquals(IpClassification.PUBLIC, hop.getIpClassification());
        assertEquals("MOCK", hop.getGeoSource());
        assertEquals("United States", hop.getCountry());
        assertNotNull(hop.getLatitude());
    }

    @Test
    void skipsGeolocationForPrivateIpsButStillClassifiesThem() {
        List<String> received = List.of(
                "from internal-relay (internal-relay [10.0.0.5]) "
                        + "by mx.target.com with ESMTP; Tue, 1 Sep 2026 10:15:20 +0000");

        List<HopEvent> hops = builder.build(received);
        HopEvent hop = hops.get(0);

        assertEquals(IpClassification.PRIVATE, hop.getIpClassification());
        assertEquals("SKIPPED_NON_PUBLIC", hop.getGeoSource());
        assertNull(hop.getCountry());
    }

    @Test
    void fallsBackToReverseRawOrderWhenTimestampsAreMissing() {
        // Neither header has a parseable timestamp; raw order is newest-first (index 0 = most
        // recent), so the fallback must place index 1 (older) before index 0 (newer).
        List<String> received = List.of(
                "from mail.example.com (mail.example.com [203.0.113.10]) by mx.target.com",
                "from smtp.example.net (smtp.example.net [198.51.100.20]) by mail.example.com");

        List<HopEvent> hops = builder.build(received);

        assertEquals(2, hops.size());
        assertNull(hops.get(0).getTimestamp());
        assertNull(hops.get(1).getTimestamp());
        assertEquals("smtp.example.net", hops.get(0).getSourceHostname());
        assertEquals("mail.example.com", hops.get(1).getSourceHostname());
        assertTrue(hops.get(0).getParsingConfidence() < 1.0);
        assertFalse(hops.get(0).getWarnings().isEmpty());
    }

    @Test
    void neverThrowsAndPreservesRawHeaderForMalformedEntries() {
        String malformed = "totally garbage received header with no structure 12345";
        List<HopEvent> hops = builder.build(List.of(malformed));

        assertEquals(1, hops.size());
        HopEvent hop = hops.get(0);
        assertEquals(malformed, hop.getRawReceivedHeader());
        assertNull(hop.getSourceHostname());
        assertNull(hop.getSourceIp());
        assertEquals(IpClassification.UNKNOWN, hop.getIpClassification());
        assertFalse(hop.getWarnings().isEmpty());
        assertEquals(1, hop.getHopNumber());
    }

    @Test
    void returnsEmptyListForEmptyOrNullInput() {
        assertTrue(builder.build(List.of()).isEmpty());
        assertTrue(builder.build(null).isEmpty());
    }
}
