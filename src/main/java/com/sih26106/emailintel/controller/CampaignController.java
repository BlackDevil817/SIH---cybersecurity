package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.ThreatCampaign;
import com.sih26106.emailintel.report.HtmlReportGenerator;
import com.sih26106.emailintel.report.PdfReportGenerator;
import com.sih26106.emailintel.repository.EmailAnalysisRepository;
import com.sih26106.emailintel.repository.ThreatCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final ThreatCampaignRepository campaignRepo;
    private final EmailAnalysisRepository  analysisRepo;
    private final HtmlReportGenerator htmlGen;
    private final PdfReportGenerator  pdfGen;

    @GetMapping
    public ResponseEntity<List<ThreatCampaign>> all() {
        return ResponseEntity.ok(campaignRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThreatCampaign> one(@PathVariable Long id) {
        return campaignRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/emails")
    public ResponseEntity<List<EmailAnalysis>> emails(@PathVariable Long id) {
        return ResponseEntity.ok(analysisRepo.findByThreatCampaignId(id));
    }

    @GetMapping("/analysis/{id}")
    public ResponseEntity<EmailAnalysis> analysis(@PathVariable Long id) {
        return analysisRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analysis/{id}/report/html")
    public ResponseEntity<String> html(@PathVariable Long id) {
        return analysisRepo.findById(id)
                .map(a -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlGen.generate(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analysis/{id}/report/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return analysisRepo.findById(id).map(a -> {
            byte[] data = pdfGen.generate(a);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(ContentDisposition.attachment().filename("report-" + id + ".pdf").build());
            return new ResponseEntity<>(data, h, HttpStatus.OK);
        }).orElse(ResponseEntity.notFound().build());
    }
}