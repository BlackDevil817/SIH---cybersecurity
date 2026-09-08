package com.sih26106.emailintel.dto;

import com.sih26106.emailintel.model.AnalysisStatus;

import java.util.List;

/**
 * Response returned by POST /api/emails/upload.
 * Deliberately minimal - never exposes server filesystem paths or internal object graphs.
 */
public record UploadResponseDto(
        String analysisId,
        String filename,
        AnalysisStatus status,
        String message,
        List<String> warnings
) {
}
