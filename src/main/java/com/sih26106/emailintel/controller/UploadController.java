package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UploadController {

    private final AnalysisService analysisService;

    @PostMapping("/upload")
    public ResponseEntity<EmailAnalysis> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty())
            return ResponseEntity.badRequest().build();
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".eml"))
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(analysisService.analyze(file));
    }
}