package com.sih26106.emailintel.geo;

import com.sih26106.emailintel.model.IpClassification;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and classifies IPv4/IPv6 addresses found in free text (typically the "from" clause
 * of a Received header, or the whole raw header as a fallback).
 *
 * Safety note: every candidate string is syntactically pre-validated by this class BEFORE it
 * is ever passed to InetAddress - InetAddress.getByName() falls back to a live DNS lookup for
 * any string it doesn't recognize as a literal IP address, and this class must never trigger
 * that (no network calls, fully deterministic, safe to run in tests/CI with no network access).
 *
 * Deliberately conservative about what counts as a candidate IP to avoid false positives from
 * port numbers, timestamps, message IDs, or other numeric text embedded in the same header.
 */
@Component
public class IpExtractor {

    private static final Pattern IPV4_CANDIDATE =
            Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");

    private static final Pattern IPV6_CANDIDATE =
            Pattern.compile("\\b((?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4})\\b");

    /** Finds the first plausible, valid IP address in the given text (IPv4 checked before IPv6). */
    public Optional<String> extractFirstIp(String text) {
        List<String> found = extractAllIps(text);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Finds all distinct, valid IP addresses in the given text, in order of first appearance. */
    public List<String> extractAllIps(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return new ArrayList<>(found);
        }

        Matcher v4 = IPV4_CANDIDATE.matcher(text);
        while (v4.find()) {
            String candidate = v4.group(1);
            if (isSyntacticallyValidIpv4(candidate)) {
                found.add(candidate);
            }
        }

        Matcher v6 = IPV6_CANDIDATE.matcher(text);
        while (v6.find()) {
            String candidate = v6.group(1);
            if (isPlausibleIpv6(candidate) && parseLiteral(candidate).isPresent()) {
                found.add(candidate);
            }
        }

        return new ArrayList<>(found);
    }

    /**
     * Classifies an already-extracted IP address string.
     * Returns INVALID if it is not actually a parseable IP; UNKNOWN if the input is null/blank.
     */
    public IpClassification classify(String ip) {
        if (ip == null || ip.isBlank()) {
            return IpClassification.UNKNOWN;
        }
        Optional<InetAddress> parsed = parseLiteral(ip);
        if (parsed.isEmpty()) {
            return IpClassification.INVALID;
        }

        InetAddress addr = parsed.get();
        if (addr.isLoopbackAddress()) {
            return IpClassification.LOOPBACK;
        }
        if (addr.isLinkLocalAddress()) {
            return IpClassification.LINK_LOCAL;
        }
        if (addr.isMulticastAddress()) {
            return IpClassification.MULTICAST;
        }
        if (addr.isSiteLocalAddress()) {
            return IpClassification.PRIVATE;
        }
        return IpClassification.PUBLIC;
    }

    /**
     * Parses a string as an IP literal WITHOUT ever risking a DNS hostname lookup: only calls
     * InetAddress.getByName after confirming the string already looks like a syntactically
     * plausible IPv4 or IPv6 literal.
     */
    private Optional<InetAddress> parseLiteral(String candidate) {
        boolean plausible = isSyntacticallyValidIpv4(candidate) || isPlausibleIpv6(candidate);
        if (!plausible) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    private boolean isSyntacticallyValidIpv4(String candidate) {
        if (candidate == null) {
            return false;
        }
        String[] parts = candidate.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                return false; // reject leading zeros (e.g. "01") - non-canonical, likely a false positive
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlausibleIpv6(String candidate) {
        if (candidate == null) {
            return false;
        }
        long colonCount = candidate.chars().filter(c -> c == ':').count();
        if (colonCount < 2) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c != ':' && Character.digit(c, 16) == -1) {
                return false;
            }
        }
        if (candidate.contains("::")) {
            // Compressed form - loosely plausible; InetAddress does the final structural check.
            return true;
        }
        // Uncompressed IPv6 must have exactly 8 groups (7 colons) - this is what rules out
        // things like "10:15:20" (a timestamp) from ever being treated as a candidate IP.
        String[] groups = candidate.split(":", -1);
        return groups.length == 8;
    }
}
