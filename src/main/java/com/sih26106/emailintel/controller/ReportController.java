package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.report.HtmlReportGenerator;
import com.sih26106.emailintel.report.PdfReportGenerator;
import com.sih26106.emailintel.repository.EmailAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final EmailAnalysisRepository repo;
    private final HtmlReportGenerator htmlGen;
    private final PdfReportGenerator pdfGen;

    @GetMapping("/{id}/html")
    public ResponseEntity<String> html(@PathVariable Long id) {
        return repo.findById(id)
                .map(a -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlGen.generate(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return repo.findById(id).map(a -> {
            byte[] data = pdfGen.generate(a);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(ContentDisposition.attachment().filename("report-" + id + ".pdf").build());
            return new ResponseEntity<>(data, h, HttpStatus.OK);
        }).orElse(ResponseEntity.notFound().build());
    }
}