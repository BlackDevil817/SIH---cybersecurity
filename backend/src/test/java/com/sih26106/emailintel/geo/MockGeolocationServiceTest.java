package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.geo.impl.MockGeolocationService;
import com.sih26106.emailintel.model.GeoLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockGeolocationServiceTest {

    private final GeolocationService service = new MockGeolocationService();

    @Test
    void returnsDeterministicMockDataForKnownDemoIp() {
        GeoLocation location = service.lookup("203.0.113.10");

        assertEquals("MOCK", location.getSource());
        assertEquals("United States", location.getCountry());
        assertEquals("Mountain View", location.getCity());
        assertNotNull(location.getLatitude());
        assertNotNull(location.getLongitude());
    }

    @Test
    void returnsSameDeterministicResultOnRepeatedCalls() {
        GeoLocation first = service.lookup("198.51.100.20");
        GeoLocation second = service.lookup("198.51.100.20");

        assertEquals(first.getCountry(), second.getCountry());
        assertEquals(first.getCity(), second.getCity());
        assertEquals(first.getLatitude(), second.getLatitude());
    }

    @Test
    void returnsUnavailableForUnknownIpWithoutInventingData() {
        GeoLocation location = service.lookup("1.2.3.4");

        assertEquals("UNAVAILABLE", location.getSource());
        assertNull(location.getCountry());
        assertNull(location.getCity());
        assertNull(location.getLatitude());
        assertNull(location.getLongitude());
    }

    @Test
    void returnsUnavailableForPrivateIpRatherThanGuessing() {
        // GeolocationService itself does not gate on classification (that's HopChainBuilder's
        // job), but it must never invent geographic data for an address it has no mapping for.
        GeoLocation location = service.lookup("192.168.1.5");

        assertEquals("UNAVAILABLE", location.getSource());
        assertNull(location.getCountry());
    }
}
