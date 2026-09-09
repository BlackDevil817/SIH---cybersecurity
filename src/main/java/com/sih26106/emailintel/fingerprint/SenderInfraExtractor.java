package com.sih26106.emailintel.fingerprint;

import com.sih26106.emailintel.geo.GeolocationService;
import com.sih26106.emailintel.geo.dto.GeoInfo;
import com.sih26106.emailintel.ioc.UrlExtractor;
import com.sih26106.emailintel.model.EmailAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SenderInfraExtractor {

    private final GeolocationService geolocationService;
    private final UrlExtractor urlExtractor;

    /**
     * Build SenderFingerprint from an EmailAnalysis.
     * Never throws — returns partial fingerprint on any failure.
     */
    public SenderFingerprint extract(EmailAnalysis analysis) {
        if (analysis == null) {
            log.warn("SenderInfraExtractor: null analysis");
            return SenderFingerprint.builder().build();
        }

        SenderFingerprint.SenderFingerprintBuilder b = SenderFingerprint.builder();

        // ── 1. IP + ASN via geolocation
        String ip = analysis.getSenderIp();
        b.originatingIp(ip);
        if (ip != null && !ip.isBlank()) {
            try {
                GeoInfo geo = geolocationService.geolocate(ip);
                if (geo.isValid() && !geo.isPrivateAddress()) {
                    b.asn(extractAsnNumber(geo.getAsn()));
                    b.asnOrganization(extractAsnOrg(geo.getAsn()));
                    b.ipRange(estimateIpRange(ip));
                }
            } catch (Exception ex) {
                log.warn("SenderInfraExtractor: geo failed for {}: {}", ip, ex.getMessage());
            }
        }

        // ── 2. Email auth features
        b.dkimDomain(lower(analysis.getDkimDomain()));
        b.dkimSelector(lower(analysis.getDkimSelector()));
        b.spfRecord(lower(analysis.getSpfResult()));
        b.dmarcPolicy(lower(analysis.getDmarcResult()));

        // ── 3. Domain features
        b.senderDomain(lower(analysis.getSenderDomain()));
        b.replyToDomain(lower(analysis.getReplyToDomain()));
        b.returnPathDomain(lower(analysis.getReturnPathDomain()));
        b.fromDomain(domainFromEmail(analysis.getFromAddress()));

        // ── 4. URL domains from IOCs
        List<String> urlDomains = new ArrayList<>();
        if (analysis.getIocs() != null) {
            analysis.getIocs().stream()
                    .filter(ioc -> "URL".equalsIgnoreCase(ioc.getType())
                            && ioc.getValue() != null)
                    .map(ioc -> domainFromUrl(ioc.getValue()))
                    .filter(d -> d != null && !d.isBlank())
                    .distinct()
                    .forEach(urlDomains::add);
        }
        b.urlDomains(urlDomains);

        SenderFingerprint fp = b.build();
        log.debug("SenderInfraExtractor: email {} → asn={} dkim={} domain={}",
                analysis.getId(), fp.getAsn(), fp.getDkimDomain(), fp.getSenderDomain());
        return fp;
    }

    /** "AS15169 Google LLC" → "AS15169" */
    public String extractAsnNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().split("\\s+", 2)[0];
    }

    /** "AS15169 Google LLC" → "Google LLC" */
    public String extractAsnOrg(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : null;
    }

    /** "8.8.8.8" → "8.8.8.0/24" */
    public String estimateIpRange(String ip) {
        if (ip == null || !ip.contains(".")) return null;
        String[] p = ip.split("\\.");
        if (p.length != 4) return null;
        return p[0] + "." + p[1] + "." + p[2] + ".0/24";
    }

    private String domainFromEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        String clean = email.replaceAll("[<>]", "").trim();
        String[] parts = clean.split("@");
        return parts.length == 2 ? parts[1].toLowerCase().trim() : null;
    }

    private String domainFromUrl(String url) {
        try { return new URI(url).getHost(); } catch (Exception e) { return null; }
    }

    private String lower(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toLowerCase();
    }
}