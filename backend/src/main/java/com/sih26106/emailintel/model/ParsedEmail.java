package com.sih26106.emailintel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured, in-memory representation produced by EmlParser.
 *
 * This exists so that no other component in the application ever needs to touch
 * jakarta.mail.internet.MimeMessage directly - MimeMessage/Session objects are not
 * thread-safe, are expensive to keep around, and leak a raw-parsing concern into
 * unrelated layers (auth checks, hop chain building, ML export, etc).
 */
public class ParsedEmail {

    private EmailHeaders headers;
    private List<AttachmentInfo> attachments = new ArrayList<>();
    private String topLevelContentType;
    private boolean multipart;

    /**
     * Non-fatal issues encountered while parsing (e.g. "could not parse Date header",
     * "MIME part N had no discernible filename"). The overall parse still succeeds.
     */
    private List<String> parsingWarnings = new ArrayList<>();

    public ParsedEmail() {
    }

    public EmailHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(EmailHeaders headers) {
        this.headers = headers;
    }

    public List<AttachmentInfo> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentInfo> attachments) {
        this.attachments = attachments;
    }

    public String getTopLevelContentType() {
        return topLevelContentType;
    }

    public void setTopLevelContentType(String topLevelContentType) {
        this.topLevelContentType = topLevelContentType;
    }

    public boolean isMultipart() {
        return multipart;
    }

    public void setMultipart(boolean multipart) {
        this.multipart = multipart;
    }

    public List<String> getParsingWarnings() {
        return parsingWarnings;
    }

    public void setParsingWarnings(List<String> parsingWarnings) {
        this.parsingWarnings = parsingWarnings;
    }

    public void addWarning(String warning) {
        this.parsingWarnings.add(warning);
    }
}
