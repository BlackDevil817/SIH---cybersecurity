package com.sih26106.emailintel.controller;

import com.sih26106.emailintel.model.*;
import com.sih26106.emailintel.report.HtmlReportGenerator;
import com.sih26106.emailintel.report.PdfReportGenerator;
import com.sih26106.emailintel.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

    @Mock ThreatCampaignRepository campaignRepo;
    @Mock EmailAnalysisRepository analysisRepo;
    @Mock HtmlReportGenerator htmlGen;
    @Mock PdfReportGenerator pdfGen;
    @InjectMocks CampaignController controller;

    @Test void testGetAll() {
        when(campaignRepo.findAll()).thenReturn(List.of(ThreatCampaign.builder().id(1L).build()));
        assertThat(controller.all().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.all().getBody()).hasSize(1);
    }

    @Test void testGetOne_found() {
        when(campaignRepo.findById(1L)).thenReturn(Optional.of(ThreatCampaign.builder().id(1L).build()));
        assertThat(controller.one(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void testGetOne_notFound() {
        when(campaignRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(controller.one(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void testHtmlReport_success() {
        EmailAnalysis a = EmailAnalysis.builder().id(1L).build();
        when(analysisRepo.findById(1L)).thenReturn(Optional.of(a));
        when(htmlGen.generate(a)).thenReturn("<html>OK</html>");
        ResponseEntity<String> r = controller.html(1L);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("OK");
    }

    @Test void testHtmlReport_notFound() {
        when(analysisRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(controller.html(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void testPdfReport_success() {
        EmailAnalysis a = EmailAnalysis.builder().id(1L).build();
        when(analysisRepo.findById(1L)).thenReturn(Optional.of(a));
        when(pdfGen.generate(a)).thenReturn(new byte[]{37, 80, 68, 70}); // %PDF
        ResponseEntity<byte[]> r = controller.pdf(1L);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}