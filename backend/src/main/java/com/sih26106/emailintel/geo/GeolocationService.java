package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.model.GeoLocation;

/**
 * Abstraction over IP geolocation lookups.
 *
 * IMPORTANT (MVP scope): implementations of this interface are NOT required to call a real
 * external geolocation provider. The active implementation for this SIH prototype
 * (MockGeolocationService) returns deterministic, clearly-labeled mock data for a small set
 * of known demo IPs, and GeoLocation.source="UNAVAILABLE" for everything else. Never treat a
 * result as verified real-world intelligence without checking GeoLocation.getSource() first.
 *
 * Swappable later for a real provider (e.g. MaxMind, ipapi) by adding a new @Service
 * implementation and wiring it in instead - callers depend only on this interface.
 */
public interface GeolocationService {

    /**
     * Looks up geographic information for a public IP address. Never throws - implementations
     * must return a GeoLocation with source="UNAVAILABLE" (and other fields null) rather than
     * fail, so a lookup problem never breaks the rest of the analysis pipeline.
     */
    GeoLocation lookup(String ip);
}
