package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.model.EmailHeaders;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts and normalizes headers from a parsed MimeMessage into the internal
 * EmailHeaders model.
 *
 * DESIGN RULE: a failure extracting any single header (malformed encoding, unparseable
 * date, weird address syntax, etc.) must never abort extraction of the rest of the email.
 * Every extraction step is individually guarded; failures are recorded as warnings on the
 * `warnings` list passed in by the caller (EmlParser owns the ParsedEmail.parsingWarnings list).
 */
@Component
public class HeaderExtractor {

    private static final Logger log = LoggerFactory.getLogger(HeaderExtractor.class);

    // RFC 5322 date format, e.g. "Tue, 1 Sep 2026 10:15:20 +0000". Jakarta Mail's
    // MimeMessage#getSentDate() already does lenient RFC 5322 parsing for us, so we
    // primarily rely on that; this formatter is a fallback for direct-header parsing.
    private static final DateTimeFormatter RFC5322_FALLBACK =
            DateTimeFormatter.ofPattern("[EEE, ]d MMM yyyy HH:mm:ss Z");

    public EmailHeaders extract(MimeMessage message, List<String> warnings) {
        EmailHeaders headers = new EmailHeaders();

        headers.setFrom(extractAddressList(message, "getFrom", warnings));
        headers.setTo(extractRecipientList(message, jakarta.mail.Message.RecipientType.TO, warnings));
        headers.setCc(extractRecipientList(message, jakarta.mail.Message.RecipientType.CC, warnings));
        headers.setReplyTo(extractAddressList(message, "getReplyTo", warnings));

        headers.setSubject(safeGet(() -> message.getSubject(), "Subject", warnings));

        String rawDate = safeGetFirstHeader(message, "Date", warnings);
        headers.setRawDate(rawDate);
        headers.setParsedDate(parseDateSafely(message, rawDate, warnings));

        headers.setMessageId(safeGet(message::getMessageID, "Message-ID", warnings));

        headers.setReturnPath(safeGetHeaderList(message, "Return-Path", warnings));

        // Ordered, never overwritten - critical for hop-chain reconstruction later.
        headers.setReceived(safeGetHeaderList(message, "Received", warnings));

        headers.setAuthenticationResults(safeGetHeaderList(message, "Authentication-Results", warnings));
        headers.setDkimSignatures(safeGetHeaderList(message, "DKIM-Signature", warnings));
        headers.setReceivedSpf(safeGetHeaderList(message, "Received-SPF", warnings));

        List<String> arc = new ArrayList<>();
        arc.addAll(safeGetHeaderList(message, "ARC-Seal", warnings));
        arc.addAll(safeGetHeaderList(message, "ARC-Message-Signature", warnings));
        arc.addAll(safeGetHeaderList(message, "ARC-Authentication-Results", warnings));
        headers.setArcHeaders(arc);

        return headers;
    }

    // ---------------------------------------------------------------------
    // Address extraction helpers
    // ---------------------------------------------------------------------

    private List<String> extractAddressList(MimeMessage message, String which, List<String> warnings) {
        try {
            Address[] addresses = "getFrom".equals(which) ? message.getFrom() : message.getReplyTo();
            return toStringList(addresses);
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not parse '%s' addresses: %s".formatted(which, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<String> extractRecipientList(MimeMessage message, jakarta.mail.Message.RecipientType type, List<String> warnings) {
        try {
            Address[] addresses = message.getRecipients(type);
            return toStringList(addresses);
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not parse recipient list (%s): %s".formatted(type, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<String> toStringList(Address[] addresses) {
        if (addresses == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Address address : addresses) {
            if (address != null) {
                result.add(address.toString());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Generic raw-header helpers
    // ---------------------------------------------------------------------

    /**
     * Returns ALL values of a header, in the order they appear in the source file.
     * Jakarta Mail's MimeMessage#getHeader(name) already returns them in encounter order.
     */
    private List<String> safeGetHeaderList(MimeMessage message, String headerName, List<String> warnings) {
        try {
            String[] values = message.getHeader(headerName);
            if (values == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(values));
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not read header '%s': %s".formatted(headerName, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private String safeGetFirstHeader(MimeMessage message, String headerName, List<String> warnings) {
        List<String> values = safeGetHeaderList(message, headerName, warnings);
        return values.isEmpty() ? null : values.get(0);
    }

    private String safeGet(ThrowingSupplier<String> supplier, String fieldName, List<String> warnings) {
        try {
            return supplier.get();
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not parse '%s': %s".formatted(fieldName, e.getMessage()));
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Date parsing
    // ---------------------------------------------------------------------

    private OffsetDateTime parseDateSafely(MimeMessage message, String rawDate, List<String> warnings) {
        // Prefer Jakarta Mail's own lenient Date header parsing first.
        try {
            java.util.Date sentDate = message.getSentDate();
            if (sentDate != null) {
                return sentDate.toInstant().atZone(ZoneId.of("UTC")).toOffsetDateTime();
            }
        } catch (MessagingException | RuntimeException e) {
            warnings.add("MimeMessage could not derive sent date: " + e.getMessage());
        }

        // Fallback: attempt to parse the raw Date header ourselves.
        if (rawDate != null && !rawDate.isBlank()) {
            try {
                return OffsetDateTime.parse(rawDate.trim(), RFC5322_FALLBACK);
            } catch (DateTimeParseException e) {
                warnings.add("Could not parse raw Date header '%s': %s".formatted(rawDate, e.getMessage()));
            }
        }

        // Never fabricate a timestamp - return null and let the caller record the gap.
        return null;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws MessagingException;
    }
}
