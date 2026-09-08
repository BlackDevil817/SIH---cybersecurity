package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.dto.UploadResponseDto;
import com.sih26106.emailintel.service.EmailUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Entry point for submitting a raw .eml file for forensic analysis.
 *
 * This controller is intentionally thin: all validation/parsing/storage logic
 * lives in EmailUploadService, per the Controller -> Service -> Parser layering
 * required by the architecture.
 */
@RestController
@RequestMapping("/api/emails")
public class UploadController {

    private final EmailUploadService emailUploadService;

    public UploadController(EmailUploadService emailUploadService) {
        this.emailUploadService = emailUploadService;
    }

    /**
     * POST /api/emails/upload
     *
     * Accepts a multipart/form-data body with a single part named "file" containing
     * a .eml message. Returns an analysisId that can later be passed to
     * POST /api/emails/{analysisId}/analyze (Phase 2).
     *
     * Never exposes server filesystem paths in the response.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(@RequestParam("file") MultipartFile file) {
        UploadResponseDto response = emailUploadService.uploadEmail(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
