package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.exception.UnsupportedEmailFormatException;
import com.sih26106.emailintel.model.AttachmentInfo;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.ParsedEmail;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parses a raw .eml byte stream into a MimeMessage and then into our internal,
 * framework-agnostic ParsedEmail model.
 *
 * Nothing downstream of this class should ever see a jakarta.mail.internet.MimeMessage -
 * that keeps the rest of the application (auth checks, hop chain builder, ML/frontend
 * DTOs) decoupled from the MIME parsing library.
 *
 * Failure modes:
 *  - Completely unreadable / non-RFC-822 content -> UnsupportedEmailFormatException (422).
 *  - Individual header/part issues -> absorbed as warnings on ParsedEmail, never thrown.
 */
@Component
public class EmlParser {

    private static final Logger log = LoggerFactory.getLogger(EmlParser.class);

    /** Safety cap on how many MIME parts we will walk, to bound work on pathological files. */
    private static final int MAX_PARTS_TO_INSPECT = 500;

    private final HeaderExtractor headerExtractor;

    public EmlParser(HeaderExtractor headerExtractor) {
        this.headerExtractor = headerExtractor;
    }

    public ParsedEmail parse(InputStream emlInputStream, String originalFilename) {
        List<String> warnings = new ArrayList<>();

        MimeMessage message;
        try {
            Session session = Session.getInstance(new Properties());
            message = new MimeMessage(session, emlInputStream);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Failed to parse '{}' as an RFC 5322 email: {}", originalFilename, e.getMessage());
            throw new UnsupportedEmailFormatException(
                    "The uploaded file could not be interpreted as a valid email (.eml) message.", e);
        }

        // A MimeMessage can sometimes be constructed even for near-empty/garbage input
        // (Jakarta Mail is lenient). Guard against a message with essentially nothing in it.
        if (!looksLikeAnEmail(message, warnings)) {
            throw new UnsupportedEmailFormatException(
                    "The uploaded file does not appear to contain any recognizable email headers.");
        }

        ParsedEmail parsedEmail = new ParsedEmail();

        EmailHeaders headers = headerExtractor.extract(message, warnings);
        parsedEmail.setHeaders(headers);

        try {
            parsedEmail.setTopLevelContentType(message.getContentType());
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not read top-level Content-Type: " + e.getMessage());
        }

        boolean multipart = false;
        List<AttachmentInfo> attachments = new ArrayList<>();
        try {
            multipart = message.isMimeType("multipart/*");
            if (multipart) {
                Object content = message.getContent();
                if (content instanceof Multipart mp) {
                    collectAttachmentMetadata(mp, attachments, warnings, new int[]{0});
                }
            }
        } catch (MessagingException | IOException | RuntimeException e) {
            // A malformed body must never fail the whole parse - headers are already extracted.
            warnings.add("Could not fully walk MIME body structure: " + e.getMessage());
        }

        parsedEmail.setMultipart(multipart);
        parsedEmail.setAttachments(attachments);
        parsedEmail.setParsingWarnings(warnings);

        return parsedEmail;
    }

    /**
     * Cheap sanity check: does this message have at least one recognizable email header?
     * Prevents treating arbitrary binary/text files as a "successfully parsed" empty email.
     */
    private boolean looksLikeAnEmail(MimeMessage message, List<String> warnings) {
        try {
            boolean hasFrom = message.getHeader("From") != null;
            boolean hasSubject = message.getHeader("Subject") != null;
            boolean hasDate = message.getHeader("Date") != null;
            boolean hasReceived = message.getHeader("Received") != null;
            boolean hasMessageId = message.getHeader("Message-ID") != null;
            return hasFrom || hasSubject || hasDate || hasReceived || hasMessageId;
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not inspect headers to validate email structure: " + e.getMessage());
            return false;
        }
    }

    /**
     * Walks MIME parts to collect attachment METADATA only (filename, content-type, approximate
     * size). Never reads/stores the decoded attachment payload. Recursion is bounded by
     * MAX_PARTS_TO_INSPECT to avoid pathological nested-multipart files causing excessive work.
     */
    private void collectAttachmentMetadata(Multipart multipart, List<AttachmentInfo> attachments,
                                            List<String> warnings, int[] partsInspected) {
        try {
            int count = multipart.getCount();
            for (int i = 0; i < count; i++) {
                if (partsInspected[0]++ > MAX_PARTS_TO_INSPECT) {
                    warnings.add("MIME part limit reached (%d) - remaining parts were not inspected."
                            .formatted(MAX_PARTS_TO_INSPECT));
                    return;
                }

                BodyPart part = multipart.getBodyPart(i);
                inspectPart(part, attachments, warnings, partsInspected);
            }
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Error while walking multipart structure: " + e.getMessage());
        }
    }

    private void inspectPart(BodyPart part, List<AttachmentInfo> attachments,
                              List<String> warnings, int[] partsInspected) {
        try {
            Object content = part.getContent();
            if (content instanceof Multipart nested) {
                collectAttachmentMetadata(nested, attachments, warnings, partsInspected);
                return;
            }

            String disposition = safeDisposition(part, warnings);
            String filename = safeFilename(part, warnings);

            boolean isAttachmentLike = Part.ATTACHMENT.equalsIgnoreCase(disposition) || filename != null;

            if (isAttachmentLike) {
                long approxSize;
                try {
                    approxSize = Math.max(part.getSize(), 0);
                } catch (MessagingException | RuntimeException e) {
                    approxSize = -1; // unknown size, not fatal
                }

                String contentType = null;
                try {
                    contentType = part.getContentType();
                } catch (MessagingException | RuntimeException e) {
                    warnings.add("Could not read content type for attachment part: " + e.getMessage());
                }

                attachments.add(new AttachmentInfo(filename, contentType, approxSize, disposition));
            }
        } catch (MessagingException | IOException | RuntimeException e) {
            warnings.add("Could not inspect a MIME body part: " + e.getMessage());
        }
    }

    private String safeDisposition(BodyPart part, List<String> warnings) {
        try {
            return part.getDisposition();
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not read Content-Disposition for a part: " + e.getMessage());
            return null;
        }
    }

    private String safeFilename(BodyPart part, List<String> warnings) {
        try {
            return part.getFileName();
        } catch (MessagingException | RuntimeException e) {
            warnings.add("Could not read filename for a part: " + e.getMessage());
            return null;
        }
    }
}
