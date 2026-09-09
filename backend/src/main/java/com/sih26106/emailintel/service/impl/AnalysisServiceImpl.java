package com.sih26106.emailintel.service.impl;

import com.sih26106.emailintel.authcheck.DkimChecker;
import com.sih26106.emailintel.authcheck.DmarcChecker;
import com.sih26106.emailintel.authcheck.SpfChecker;
import com.sih26106.emailintel.exception.AnalysisNotFoundException;
import com.sih26106.emailintel.model.AnalysisStatus;
import com.sih26106.emailintel.model.AuthenticationSummary;
import com.sih26106.emailintel.model.DkimResult;
import com.sih26106.emailintel.model.DmarcResult;
import com.sih26106.emailintel.model.EmailAnalysis;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.HopEvent;
import com.sih26106.emailintel.model.SpfResult;
import com.sih26106.emailintel.parser.HopChainBuilder;
import com.sih26106.emailintel.service.AnalysisService;
import com.sih26106.emailintel.service.AnalysisStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the analysis pipeline: load -> run SPF/DKIM/DMARC checkers -> run Received-header
 * analysis / IP extraction+classification / geolocation / hop-chain building -> populate
 * AuthenticationSummary + hopChain -> mark ANALYZED -> persist.
 *
 * Phase 3 note: hop-chain building is independent of the SPF/DKIM/DMARC checks (they run on
 * different header data - Authentication-Results/DKIM-Signature/Received-SPF vs. Received) and
 * neither pipeline affects the other. If hop-chain building for a given email produces
 * additional warnings, those are appended to the analysis's overall warnings list, exactly like
 * the Phase 2 checker warnings already were - existing Phase 2 behavior is unchanged.
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);

    private final AnalysisStoreService analysisStoreService;
    private final SpfChecker spfChecker;
    private final DkimChecker dkimChecker;
    private final DmarcChecker dmarcChecker;
    private final HopChainBuilder hopChainBuilder;

    public AnalysisServiceImpl(AnalysisStoreService analysisStoreService,
                                SpfChecker spfChecker,
                                DkimChecker dkimChecker,
                                DmarcChecker dmarcChecker,
                                HopChainBuilder hopChainBuilder) {
        this.analysisStoreService = analysisStoreService;
        this.spfChecker = spfChecker;
        this.dkimChecker = dkimChecker;
        this.dmarcChecker = dmarcChecker;
        this.hopChainBuilder = hopChainBuilder;
    }

    @Override
    public EmailAnalysis analyze(String analysisId) {
        EmailAnalysis analysis = analysisStoreService.findById(analysisId)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisId));

        EmailHeaders headers = analysis.getHeaders();

        // --- Phase 2: SPF / DKIM / DMARC (unchanged) ---
        SpfResult spf = spfChecker.check(headers);
        DkimResult dkim = dkimChecker.check(headers);
        DmarcResult dmarc = dmarcChecker.check(headers, spf, dkim);

        AuthenticationSummary summary = new AuthenticationSummary();
        summary.setSpf(spf);
        summary.setDkim(dkim);
        summary.setDmarc(dmarc);
        analysis.setAuthentication(summary);

        analysis.getWarnings().addAll(spf.getWarnings());
        analysis.getWarnings().addAll(dkim.getWarnings());
        analysis.getWarnings().addAll(dmarc.getWarnings());

        // --- Phase 3: Received-header analysis -> IP extraction/classification -> geolocation
        //     -> hop chain ---
        List<HopEvent> hopChain = hopChainBuilder.build(
                headers != null ? headers.getReceived() : List.of());
        analysis.setHopChain(hopChain);

        for (HopEvent hop : hopChain) {
            for (String warning : hop.getWarnings()) {
                analysis.getWarnings().add("Hop " + hop.getHopNumber() + ": " + warning);
            }
        }

        analysis.setStatus(AnalysisStatus.ANALYZED);

        EmailAnalysis updated = analysisStoreService.update(analysis);
        log.info("Analyzed analysisId={} -> spf={}, dkim={}, dmarc={}, hops={}",
                analysisId, spf.getResult(), dkim.getResult(), dmarc.getResult(), hopChain.size());
        return updated;
    }

    @Override
    public EmailAnalysis getAnalysis(String analysisId) {
        return analysisStoreService.findById(analysisId)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisId));
    }
}
