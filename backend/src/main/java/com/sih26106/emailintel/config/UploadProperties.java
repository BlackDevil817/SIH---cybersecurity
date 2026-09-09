package com.sih26106.emailintel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds to `app.upload.*` in application.yml.
 * Centralizing these here means file-size limits / allowed types can be changed
 * without touching validator or controller code.
 */
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** Maximum accepted upload size in bytes. */
    private long maxSizeBytes = 15_728_640L; // 15 MB default

    /** Allowed file extensions (case-insensitive), e.g. ".eml". */
    private List<String> allowedExtensions = List.of(".eml");

    /** Allowed MIME content-types reported by the client (advisory only, never fully trusted). */
    private List<String> allowedContentTypes = List.of(
            "message/rfc822", "application/octet-stream", "text/plain"
    );

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
