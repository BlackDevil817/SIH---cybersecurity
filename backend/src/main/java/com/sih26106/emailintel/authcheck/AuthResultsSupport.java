package com.sih26106.emailintel.authcheck;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, best-effort regex helpers for interpreting RFC 8601 Authentication-Results
 * header values and related SPF/DKIM/DMARC evidence.
 *
 * Real-world Authentication-Results headers vary a lot between mail server implementations,
 * so every extraction method here returns Optional.empty() rather than throwing when a
 * pattern doesn't match - a single odd/malformed header must never break analysis of the
 * rest of the email.
 */
final class AuthResultsSupport {

    private AuthResultsSupport() {
    }

    /**
     * One method's result segment within an Authentication-Results header, e.g. for
     * "spf=pass smtp.mailfrom=sender@example.net":
     *   resultToken = "pass"
     *   remainder   = " smtp.mailfrom=sender@example.net" (everything up to the next ';')
     *   rawMatch    = "spf=pass smtp.mailfrom=sender@example.net"
     */
    record MethodSegment(String resultToken, String remainder, String rawMatch) {
    }

    /** Collapses folded-header line breaks so regexes can work against a single line. */
    static String normalize(String headerValue) {
        if (headerValue == null) {
            return "";
        }
        return headerValue.replaceAll("\\r?\\n\\s*", " ").trim();
    }

    /**
     * Scans the given Authentication-Results header values (there may be more than one such
     * header) for the first one that reports the given method (e.g. "spf", "dkim", "dmarc"),
     * and extracts its result token plus the remainder of that resinfo segment up to the next ';'.
     */
    static Optional<MethodSegment> findMethodSegment(List<String> authResultsHeaders, String methodName) {
        Pattern pattern = Pattern.compile(
                "(?i)\\b" + Pattern.quote(methodName) + "\\s*=\\s*([a-zA-Z0-9_-]+)([^;]*)");
        for (String header : authResultsHeaders) {
            String normalized = normalize(header);
            Matcher m = pattern.matcher(normalized);
            if (m.find()) {
                return Optional.of(new MethodSegment(m.group(1), m.group(2), m.group(0).trim()));
            }
        }
        return Optional.empty();
    }

    /** Extracts a "tagName=value" style token from free text, tolerant of trailing punctuation. */
    static Optional<String> extractTag(String text, String tagName) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(tagName) + "\\s*=\\s*([^\\s;)]+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        String value = m.group(1).replaceAll("[,;]+$", "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** Extracts the domain portion of an email-like or "Name <addr>" style value, lower-cased. */
    static Optional<String> extractDomainFromAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return Optional.empty();
        }
        Matcher m = Pattern.compile("([^\\s<>@]+)@([^\\s<>@;,]+)").matcher(rawAddress);
        return m.find() ? Optional.of(m.group(2).toLowerCase(Locale.ROOT)) : Optional.empty();
    }
}
