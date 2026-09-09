package com.sih26106.emailintel.parser;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the raw, ordered "Received:" header values already extracted onto EmailHeaders
 * (by HeaderExtractor in Phase 1) into best-effort structured ParsedReceivedHeader objects.
 *
 * Received headers are UNTRUSTED, attacker-influenceable input and their format varies a lot
 * between mail server implementations. This analyzer is deliberately lenient:
 *  - A field that cannot be reliably extracted is left null - never guessed at.
 *  - A single malformed header can never throw and abort analysis of the rest of the email;
 *    any failure is caught and recorded as a warning on that header's ParsedReceivedHeader.
 *  - The original header order (as stored in EmailHeaders.getReceived(), newest-first) is
 *    preserved via `originalIndex` - re-ordering into chronological order is HopChainBuilder's
 *    job, not this class's.
 */
@Component
public class ReceivedHeaderAnalyzer {

    // Captures everything after "from" up to (not including) " by ", or to the end of the
    // clause text if there is no "by" segment.
    private static final Pattern FROM_CLAUSE = Pattern.compile("(?i)\\bfrom\\s+(.*?)(?=\\s+by\\s+|$)");

    // Captures everything after "by" up to the next known keyword (with/id/for/via), or to the
    // end of the clause text.
    private static final Pattern BY_CLAUSE =
            Pattern.compile("(?i)\\bby\\s+(.*?)(?=\\s+(?:with|id|for|via)\\b|$)");

    private static final Pattern WITH_PROTOCOL = Pattern.compile("(?i)\\bwith\\s+([^\\s;]+)");

    // Same lenient RFC 5322 date pattern already used by HeaderExtractor for the Date header,
    // reused here for consistency. E.g. "Tue, 1 Sep 2026 10:15:20 +0000".
    //
    // IMPORTANT: Locale.ENGLISH is pinned explicitly. RFC 5322 mandates English day/month
    // abbreviations regardless of the receiving machine's locale - without this, parsing
    // month/day names via DateTimeFormatter silently depends on the JVM's DEFAULT locale at
    // startup, and fails (caught, producing a null timestamp) on any machine whose default
    // locale doesn't recognize "Tue"/"Sep" etc. as valid English abbreviations.
    private static final DateTimeFormatter RFC5322_DATE_FORMAT =
            DateTimeFormatter.ofPattern("[EEE, ]d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /**
     * Parses every Received header in the given (already ordered, newest-first) list.
     * Never throws - a malformed header just produces a ParsedReceivedHeader with fewer
     * fields populated and warnings explaining what could not be determined.
     */
    public List<ParsedReceivedHeader> parse(List<String> receivedHeaders) {
        List<ParsedReceivedHeader> results = new ArrayList<>();
        if (receivedHeaders == null) {
            return results;
        }
        for (int i = 0; i < receivedHeaders.size(); i++) {
            results.add(parseOne(receivedHeaders.get(i), i));
        }
        return results;
    }

    private ParsedReceivedHeader parseOne(String rawHeader, int index) {
        ParsedReceivedHeader parsed = new ParsedReceivedHeader();
        parsed.setOriginalIndex(index);
        parsed.setRawHeader(rawHeader);
        List<String> warnings = new ArrayList<>();
        parsed.setWarnings(warnings);

        try {
            String normalized = normalize(rawHeader);

            // The trailing date-time is conventionally separated from the rest of the header
            // by the LAST semicolon - this is the standard, safest split point.
            int lastSemicolon = normalized.lastIndexOf(';');
            String clausePart = lastSemicolon >= 0 ? normalized.substring(0, lastSemicolon) : normalized;
            String datePart = lastSemicolon >= 0 ? normalized.substring(lastSemicolon + 1).trim() : null;

            Matcher fromMatcher = FROM_CLAUSE.matcher(clausePart);
            if (fromMatcher.find()) {
                String fromRaw = fromMatcher.group(1).trim();
                if (!fromRaw.isEmpty()) {
                    parsed.setFromRaw(fromRaw);
                    parsed.setFromHost(firstToken(fromRaw));
                }
            }
            if (parsed.getFromRaw() == null) {
                warnings.add("Could not find a 'from' clause in this Received header.");
            }

            Matcher byMatcher = BY_CLAUSE.matcher(clausePart);
            if (byMatcher.find()) {
                String byRaw = byMatcher.group(1).trim();
                if (!byRaw.isEmpty()) {
                    parsed.setByRaw(byRaw);
                    parsed.setByHost(firstToken(byRaw));
                }
            }
            if (parsed.getByRaw() == null) {
                warnings.add("Could not find a 'by' clause in this Received header.");
            }

            Matcher protoMatcher = WITH_PROTOCOL.matcher(clausePart);
            if (protoMatcher.find()) {
                parsed.setProtocol(protoMatcher.group(1));
            }

            if (datePart != null && !datePart.isEmpty()) {
                parsed.setRawTimestamp(datePart);
                Optional<OffsetDateTime> parsedDate = parseTimestamp(datePart);
                if (parsedDate.isPresent()) {
                    parsed.setParsedTimestamp(parsedDate.get());
                } else {
                    warnings.add("Could not parse Received header timestamp: '" + datePart + "'.");
                }
            } else {
                warnings.add("Received header has no trailing timestamp (no ';' found, or nothing after it).");
            }

        } catch (RuntimeException e) {
            // Received headers are untrusted input - a single odd header must never abort
            // analysis of the rest of the email.
            warnings.add("Failed to parse Received header: " + e.getMessage());
        }

        return parsed;
    }

    /** First whitespace-delimited token of the text, with any trailing "(" comment stripped. */
    private String firstToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        int spaceIdx = trimmed.indexOf(' ');
        String token = spaceIdx >= 0 ? trimmed.substring(0, spaceIdx) : trimmed;
        int parenIdx = token.indexOf('(');
        String result = parenIdx > 0 ? token.substring(0, parenIdx) : token;
        return result.isBlank() ? null : result;
    }

    private Optional<OffsetDateTime> parseTimestamp(String rawDate) {
        try {
            return Optional.of(OffsetDateTime.parse(rawDate.trim(), RFC5322_DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Collapses folded-header line breaks so regexes can work against a single line. */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\r?\\n\\s*", " ").trim();
    }
}
