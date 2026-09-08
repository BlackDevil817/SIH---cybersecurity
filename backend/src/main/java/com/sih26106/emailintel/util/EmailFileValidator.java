package com.sih26106.emailintel.util;

import com.sih26106.emailintel.config.UploadProperties;
import com.sih26106.emailintel.exception.FileTooLargeException;
import com.sih26106.emailintel.exception.InvalidEmlException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * First line of defense for uploads. Only performs cheap, non-parsing checks
 * (size, extension, basic emptiness) - actual structural validation of the
 * email content happens in EmlParser. Never touches the filesystem.
 */
@Component
public class EmailFileValidator {

    private final UploadProperties uploadProperties;

    public EmailFileValidator(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidEmlException("No file was uploaded, or the uploaded file is empty.");
        }

        if (file.getSize() > uploadProperties.getMaxSizeBytes()) {
            throw new FileTooLargeException(
                    "File size %d bytes exceeds the maximum allowed size of %d bytes."
                            .formatted(file.getSize(), uploadProperties.getMaxSizeBytes()));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidEmlException("Uploaded file has no filename.");
        }

        // Basic path-safety: reject filenames that look like they carry path segments.
        if (originalFilename.contains("/") || originalFilename.contains("\\") || originalFilename.contains("..")) {
            throw new InvalidEmlException("Uploaded filename contains invalid path characters.");
        }

        String lower = originalFilename.toLowerCase(Locale.ROOT);
        boolean hasAllowedExtension = uploadProperties.getAllowedExtensions().stream()
                .anyMatch(ext -> lower.endsWith(ext.toLowerCase(Locale.ROOT)));

        if (!hasAllowedExtension) {
            throw new InvalidEmlException(
                    "Unsupported file extension. Allowed extensions: " + uploadProperties.getAllowedExtensions());
        }

        // Content-Type is client-reported and easily spoofed, so it is advisory only:
        // we log/allow a mismatch rather than reject on it - the real check happens
        // when EmlParser actually tries to parse the bytes as an RFC 5322 message.
    }
}
