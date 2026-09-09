package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.repository.EmailAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalyzeController {

    private final EmailAnalysisRepository repo;

    @GetMapping
    public ResponseEntity<List<EmailAnalysis>> all() {
        return ResponseEntity.ok(repo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailAnalysis> one(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
