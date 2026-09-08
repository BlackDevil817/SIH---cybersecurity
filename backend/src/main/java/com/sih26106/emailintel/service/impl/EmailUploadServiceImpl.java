package com.sih26106.emailintel.service.impl;

import com.sih26106.emailintel.dto.UploadResponseDto;
import com.sih26106.emailintel.exception.UnsupportedEmailFormatException;
import com.sih26106.emailintel.model.AnalysisStatus;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.ParsedEmail;
import com.sih26106.emailintel.parser.EmlParser;
import com.sih26106.emailintel.service.AnalysisStoreService;
import com.sih26106.emailintel.service.EmailUploadService;
import com.sih26106.emailintel.util.EmailFileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Orchestrates: validate -> parse -> store -> respond.
 *
 * Note on design: we parse synchronously at upload time (see architecture notes) so that
 * unprocessable files are rejected immediately with a clear error, rather than accepted
 * and only failing later when /analyze is called. The heavier forensic work (auth checks,
 * IP/geo, hop chain) is deferred to the /analyze endpoint (Phase 2+).
 */
@Service
public class EmailUploadServiceImpl implements EmailUploadService {

    private static final Logger log = LoggerFactory.getLogger(EmailUploadServiceImpl.class);

    private final EmailFileValidator validator;
    private final EmlParser emlParser;
    private final AnalysisStoreService analysisStoreService;

    public EmailUploadServiceImpl(EmailFileValidator validator,
                                   EmlParser emlParser,
                                   AnalysisStoreService analysisStoreService) {
        this.validator = validator;
        this.emlParser = emlParser;
        this.analysisStoreService = analysisStoreService;
    }

    @Override
    public UploadResponseDto uploadEmail(MultipartFile file) {
        validator.validate(file);

        String analysisId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();

        ParsedEmail parsedEmail;
        try (InputStream inputStream = file.getInputStream()) {
            parsedEmail = emlParser.parse(inputStream, filename);
        } catch (IOException e) {
            // Could not even read the multipart stream - this is an upload-level problem, not
            // a malformed-email problem.
            log.error("I/O error reading uploaded file '{}': {}", filename, e.getMessage());
            throw new UnsupportedEmailFormatException("Could not read the uploaded file.", e);
        }

        EmailAnalysis analysis = new EmailAnalysis();
        analysis.setAnalysisId(analysisId);
        analysis.setFilename(filename);
        analysis.setStatus(AnalysisStatus.PARSED);
        analysis.setHeaders(parsedEmail.getHeaders());
        analysis.getWarnings().addAll(parsedEmail.getParsingWarnings());

        analysisStoreService.save(analysis);

        log.info("Uploaded and parsed email '{}' -> analysisId={} ({} warnings, {} Received headers)",
                filename, analysisId, parsedEmail.getParsingWarnings().size(),
                parsedEmail.getHeaders().getReceived().size());

        return new UploadResponseDto(
                analysisId,
                filename,
                AnalysisStatus.PARSED,
                "File uploaded and parsed successfully. Call /api/emails/{analysisId}/analyze to run forensic analysis.",
                parsedEmail.getParsingWarnings()
        );
    }
}
