package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.config.AppConfig;
import com.sih26106.emailintel.geo.dto.GeoInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeolocationService {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private static final Set<String> PRIVATE_PREFIXES = Set.of(
            "10.", "127.", "169.254.",
            "172.16.", "172.17.", "172.18.", "172.19.", "172.20.",
            "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "0.", "255."
    );

    public GeoInfo geolocate(String ip) {
        if (ip == null || ip.isBlank()) {
            log.warn("GeolocationService: null/blank IP");
            return GeoInfo.unavailable("null");
        }

        String clean = ip.trim();

        if (!isValidIp(clean)) {
            log.warn("GeolocationService: invalid IP format '{}'", clean);
            GeoInfo r = GeoInfo.unavailable(clean);
            r.setErrorMessage("Invalid IP format");
            return r;
        }

        if (isPrivateIp(clean)) {
            log.debug("GeolocationService: private IP skipped '{}'", clean);
            return GeoInfo.privateIp(clean);
        }

        try {
            // ip-api.com free endpoint — no key required for <45 req/min
            String url = appConfig.getGeoApiUrl()
                    + "/" + clean
                    + "?fields=status,message,country,countryCode,region,regionName"
                    + ",city,lat,lon,timezone,isp,org,as,query";

            log.debug("GeolocationService: GET {}", url);
            GeoInfo response = restTemplate.getForObject(url, GeoInfo.class);

            if (response == null) {
                log.warn("GeolocationService: null response for '{}'", clean);
                return GeoInfo.unavailable(clean);
            }
            if ("fail".equalsIgnoreCase(response.getStatus())) {
                log.warn("GeolocationService: API fail for '{}': {}", clean, response.getErrorMessage());
                return GeoInfo.unavailable(clean);
            }

            response.setIp(clean);
            response.setValid(true);
            response.setPrivateAddress(false);
            log.debug("GeolocationService: {} → {}, {}", clean, response.getCity(), response.getCountry());
            return response;

        } catch (RestClientException ex) {
            log.error("GeolocationService: HTTP error for '{}': {}", clean, ex.getMessage());
            return GeoInfo.unavailable(clean);
        } catch (Exception ex) {
            log.error("GeolocationService: unexpected error for '{}'", clean, ex);
            return GeoInfo.unavailable(clean);
        }
    }

    public boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return IPV4.matcher(ip).matches() || ip.contains(":");   // IPv4 or IPv6
    }

    public boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        for (String prefix : PRIVATE_PREFIXES) {
            if (ip.startsWith(prefix)) return true;
        }
        return "::1".equals(ip);
    }
}