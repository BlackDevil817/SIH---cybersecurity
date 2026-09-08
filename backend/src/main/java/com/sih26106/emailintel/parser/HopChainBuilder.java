package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.geo.GeolocationService;
import com.sih26106.emailintel.geo.IpExtractor;
import com.sih26106.emailintel.model.GeoLocation;
import com.sih26106.emailintel.model.HopEvent;
import com.sih26106.emailintel.model.IpClassification;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Builds a chronologically-ordered List<HopEvent> from an email's raw Received headers, by
 * combining ReceivedHeaderAnalyzer (structural parsing), IpExtractor (IP extraction +
 * classification) and GeolocationService (geo enrichment of PUBLIC IPs only).
 *
 * ORDERING ALGORITHM:
 *  1. EmailHeaders.getReceived() is stored newest-first (index 0 = the header closest to the
 *     top of the file = the LAST hop added = chronologically the MOST RECENT). This class does
 *     NOT assume that raw order is already chronological.
 *  2. Primary sort key: parsed timestamp, ascending (oldest first -> origin-to-recipient order).
 *  3. Fallback sort key (used only when a timestamp is missing/unparseable, or to break ties):
 *     the reverse of the header's original position in the raw list. Since raw order is
 *     newest-first, reversing it gives oldest-first - consistent with headers that DO have
 *     timestamps. This is a best-effort default, not a verified fact.
 *  4. Timestamps are NEVER fabricated - a hop with no parseable timestamp keeps timestamp=null
 *     and is flagged with a reduced parsingConfidence and an explicit warning.
 *
 * Final hopNumber (1-based) reflects the resulting chronological position, not the raw header index.
 */
@Component
public class HopChainBuilder {

    private final ReceivedHeaderAnalyzer receivedHeaderAnalyzer;
    private final IpExtractor ipExtractor;
    private final GeolocationService geolocationService;

    public HopChainBuilder(ReceivedHeaderAnalyzer receivedHeaderAnalyzer,
                            IpExtractor ipExtractor,
                            GeolocationService geolocationService) {
        this.receivedHeaderAnalyzer = receivedHeaderAnalyzer;
        this.ipExtractor = ipExtractor;
        this.geolocationService = geolocationService;
    }

    /**
     * @param receivedHeaders raw Received header values, newest-first (as stored on EmailHeaders)
     * @return HopEvents in chronological order (origin first), 1-based hopNumber, never null
     */
    public List<HopEvent> build(List<String> receivedHeaders) {
        List<ParsedReceivedHeader> parsedHeaders = receivedHeaderAnalyzer.parse(receivedHeaders);

        List<Indexed> indexed = new ArrayList<>();
        for (ParsedReceivedHeader parsed : parsedHeaders) {
            indexed.add(new Indexed(parsed, toHopEvent(parsed)));
        }

        indexed.sort(
                Comparator.<Indexed, OffsetDateTime>comparing(e -> e.hop.getTimestamp(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(e -> -e.parsed.getOriginalIndex()));

        List<HopEvent> hops = new ArrayList<>();
        for (int i = 0; i < indexed.size(); i++) {
            HopEvent hop = indexed.get(i).hop;
            hop.setHopNumber(i + 1);
            hops.add(hop);
        }
        return hops;
    }

    private HopEvent toHopEvent(ParsedReceivedHeader parsed) {
        HopEvent hop = new HopEvent();
        hop.setSourceHostname(parsed.getFromHost());
        hop.setDestinationHostname(parsed.getByHost());
        hop.setProtocol(parsed.getProtocol());
        hop.setTimestamp(parsed.getParsedTimestamp());
        hop.setRawReceivedHeader(parsed.getRawHeader());

        List<String> warnings = new ArrayList<>(parsed.getWarnings());

        if (parsed.getParsedTimestamp() == null) {
            hop.setParsingConfidence(0.5);
            warnings.add("No usable timestamp for this hop; ordering falls back to the header's "
                    + "raw (newest-first) position reversed, which may not reflect the true "
                    + "chronological order.");
        }

        String searchText = (parsed.getFromRaw() != null ? parsed.getFromRaw() : "")
                + " " + (parsed.getRawHeader() != null ? parsed.getRawHeader() : "");
        Optional<String> ip = ipExtractor.extractFirstIp(searchText);

        if (ip.isPresent()) {
            hop.setSourceIp(ip.get());
            IpClassification classification = ipExtractor.classify(ip.get());
            hop.setIpClassification(classification);
            applyGeoLocation(hop, ip.get(), classification, warnings);
        } else {
            hop.setIpClassification(IpClassification.UNKNOWN);
            warnings.add("Could not extract a source IP address from this Received header.");
        }

        hop.setWarnings(warnings);
        return hop;
    }

    private void applyGeoLocation(HopEvent hop, String ip, IpClassification classification, List<String> warnings) {
        if (classification != IpClassification.PUBLIC) {
            hop.setGeoSource("SKIPPED_NON_PUBLIC");
            warnings.add("Source IP " + ip + " is classified as " + classification
                    + "; skipping geolocation (only PUBLIC addresses are geolocated).");
            return;
        }

        GeoLocation location = geolocationService.lookup(ip);
        hop.setCountry(location.getCountry());
        hop.setCity(location.getCity());
        hop.setRegion(location.getRegion());
        hop.setLatitude(location.getLatitude());
        hop.setLongitude(location.getLongitude());
        hop.setGeoSource(location.getSource());

        if ("UNAVAILABLE".equals(location.getSource())) {
            warnings.add("Geolocation unavailable for " + ip + ".");
        } else if ("MOCK".equals(location.getSource())) {
            warnings.add("Geolocation for " + ip + " is MOCK/demo data, not verified real-world intelligence.");
        }
    }

    /** Pairs a ParsedReceivedHeader with its (not-yet-numbered) HopEvent for sorting purposes. */
    private static final class Indexed {
        private final ParsedReceivedHeader parsed;
        private final HopEvent hop;

        private Indexed(ParsedReceivedHeader parsed, HopEvent hop) {
            this.parsed = parsed;
            this.hop = hop;
        }
    }
}
