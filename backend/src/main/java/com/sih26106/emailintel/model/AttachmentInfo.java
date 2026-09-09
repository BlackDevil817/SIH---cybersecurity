package com.sih26106.emailintel.model;

/**
 * Metadata-only description of a MIME attachment/body part.
 * Per security requirements we NEVER retain attachment content bytes here.
 */
public class AttachmentInfo {

    private String filename;
    private String contentType;
    private long approximateSizeBytes;
    private String contentDisposition; // "attachment", "inline", or null if unspecified

    public AttachmentInfo() {
    }

    public AttachmentInfo(String filename, String contentType, long approximateSizeBytes, String contentDisposition) {
        this.filename = filename;
        this.contentType = contentType;
        this.approximateSizeBytes = approximateSizeBytes;
        this.contentDisposition = contentDisposition;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getApproximateSizeBytes() {
        return approximateSizeBytes;
    }

    public void setApproximateSizeBytes(long approximateSizeBytes) {
        this.approximateSizeBytes = approximateSizeBytes;
    }

    public String getContentDisposition() {
        return contentDisposition;
    }

    public void setContentDisposition(String contentDisposition) {
        this.contentDisposition = contentDisposition;
    }

    @Override
    public String toString() {
        return "AttachmentInfo{filename='%s', contentType='%s', size=%d, disposition='%s'}"
                .formatted(filename, contentType, approximateSizeBytes, contentDisposition);
    }
}
