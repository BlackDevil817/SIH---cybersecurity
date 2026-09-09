package com.sih26106.emailintel.model;

/**
 * Container for SPF/DKIM/DMARC results, populated by AnalysisService during
 * POST /api/emails/{analysisId}/analyze (Phase 2).
 *
 * Replaces the Phase 1 generic Map-based stub now that SpfChecker/DkimChecker/DmarcChecker
 * exist. Position within EmailAnalysis is unchanged, so this does not affect any already-agreed
 * JSON contract shape for the ML/frontend teams beyond making the fields concrete.
 */
public class AuthenticationSummary {

    private SpfResult spf;
    private DkimResult dkim;
    private DmarcResult dmarc;

    public SpfResult getSpf() {
        return spf;
    }

    public void setSpf(SpfResult spf) {
        this.spf = spf;
    }

    public DkimResult getDkim() {
        return dkim;
    }

    public void setDkim(DkimResult dkim) {
        this.dkim = dkim;
    }

    public DmarcResult getDmarc() {
        return dmarc;
    }

    public void setDmarc(DmarcResult dmarc) {
        this.dmarc = dmarc;
    }
}
