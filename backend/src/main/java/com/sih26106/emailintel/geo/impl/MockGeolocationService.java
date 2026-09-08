package com.sih26106.emailintel.geo.impl;

import com.sih26106.emailintel.geo.GeolocationService;
import com.sih26106.emailintel.model.GeoLocation;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Deterministic, MVP-only geolocation implementation for the SIH demo.
 *
 * Returns hand-picked, clearly-labeled mock data (source="MOCK") for a small fixed set of
 * demo IPs - including the IPs used in this project's own sample test fixtures, so a demo
 * upload/analyze run produces a populated hop chain out of the box - and source="UNAVAILABLE"
 * (all other fields null) for every other IP.
 *
 * THIS IS NOT REAL GEOLOCATION INTELLIGENCE. Nothing here should be presented to a user as a
 * verified fact. Replace with a real provider-backed GeolocationService implementation
 * post-MVP; every other class depends only on the GeolocationService interface, so swapping
 * this out requires no other code changes.
 */
@Service
public class MockGeolocationService implements GeolocationService {

    private static final Map<String, GeoLocation> KNOWN_DEMO_IPS = Map.of(
            "203.0.113.10", mock("United States", "California", "Mountain View", 37.386, -122.0838, "America/Los_Angeles"),
            "198.51.100.20", mock("Germany", "Berlin", "Berlin", 52.52, 13.405, "Europe/Berlin"),
            "185.10.20.30", mock("Netherlands", "North Holland", "Amsterdam", 52.3676, 4.9041, "Europe/Amsterdam")
    );

    @Override
    public GeoLocation lookup(String ip) {
        GeoLocation known = KNOWN_DEMO_IPS.get(ip);
        if (known != null) {
            return known;
        }
        GeoLocation unavailable = new GeoLocation();
        unavailable.setSource("UNAVAILABLE");
        return unavailable;
    }

    private static GeoLocation mock(String country, String region, String city,
                                     double lat, double lon, String timezone) {
        GeoLocation location = new GeoLocation();
        location.setCountry(country);
        location.setRegion(region);
        location.setCity(city);
        location.setLatitude(lat);
        location.setLongitude(lon);
        location.setTimezone(timezone);
        location.setSource("MOCK");
        return location;
    }
}
