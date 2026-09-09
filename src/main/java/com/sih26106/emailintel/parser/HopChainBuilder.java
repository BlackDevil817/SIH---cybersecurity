package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.geo.GeolocationService;
import com.sih26106.emailintel.geo.dto.GeoInfo;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.HopEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class HopChainBuilder {

    private final GeolocationService geolocationService;

    private static final Pattern IP = Pattern.compile(
            "\\b((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");
    private static final Pattern HOSTNAME = Pattern.compile(
            "from\\s+([\\w.-]+)", Pattern.CASE_INSENSITIVE);

    public List<HopEvent> build(String[] received, EmailAnalysis analysis) {
        List<HopEvent> hops = new ArrayList<>();
        if (received == null || received.length == 0) return hops;

        // Received headers newest-first; reverse to get chronological order
        for (int i = received.length - 1; i >= 0; i--) {
            String header = received[i];
            int order     = received.length - i;
            String ip     = extractIp(header);
            String host   = extractHostname(header);

            GeoInfo geo = GeoInfo.unavailable(ip);
            if (ip != null) {
                try { geo = geolocationService.geolocate(ip); }
                catch (Exception ex) { log.debug("HopChainBuilder: geo failed hop {}", order); }
            }

            hops.add(HopEvent.builder()
                    .hopOrder(order)
                    .ip(ip)
                    .hostname(host)
                    .timestamp(Instant.now())
                    .geoCountry(geo.getCountry())
                    .geoCountryCode(geo.getCountryCode())
                    .geoCity(geo.getCity())
                    .geoRegion(geo.getRegionName())
                    .geoLat(geo.getLat())
                    .geoLon(geo.getLon())
                    .geoTimezone(geo.getTimezone())
                    .geoOrg(geo.getOrg())
                    .geoAsn(geo.getAsn())
                    .emailAnalysis(analysis)
                    .build());
        }
        return hops;
    }

    private String extractIp(String h) {
        Matcher m = IP.matcher(h); return m.find() ? m.group() : null;
    }
    private String extractHostname(String h) {
        Matcher m = HOSTNAME.matcher(h); return m.find() ? m.group(1) : null;
    }
}