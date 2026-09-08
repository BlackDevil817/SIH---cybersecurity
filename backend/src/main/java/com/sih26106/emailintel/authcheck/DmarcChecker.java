package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.AlignmentStatus;
import com.sih26106.emailintel.model.DkimResult;
import com.sih26106.emailintel.model.DmarcResult;
import com.sih26106.emailintel.model.DmarcResultCode;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.SpfResult;
import com.sih26106.emailintel.model.VerificationMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interprets DMARC evidence already reported by the receiving mail server, and computes
 * SPF/DKIM alignment against the visible From domain.
 *
 * IMPORTANT: this class does NOT fetch the domain's DMARC DNS TXT record. The `result` and
 * `policy` fields reflect only what the receiving server already recorded in
 * Authentication-Results. Alignment is computed locally using STRICT (exact-match) comparison
 * only - relaxed/organizational-domain alignment is a known, documented limitation.
 */
@Component
public class DmarcChecker {

    public DmarcResult check(EmailHeaders headers, SpfResult spfResult, DkimResult dkimResult) {
        DmarcResult result = new DmarcResult();
        List<String> warnings = new ArrayList<>();
        result.setWarnings(warnings);

        List<String> from = headers.getFrom();
        if (from.isEmpty()) {
            warnings.add("No From header present; cannot determine the DMARC From domain or check alignment.");
        } else {
            if (from.size() > 1) {
                warnings.add("Multiple From addresses present (" + from.size() + "); using the first for DMARC domain.");
            }
            AuthResultsSupport.extractDomainFromAddress(from.get(0)).ifPresent(result::setFromDomain);
        }

        List<String> authResults = headers.getAuthenticationResults();
        if (authResults.size() > 1) {
            warnings.add("Multiple Authentication-Results headers present (" + authResults.size()
                    + "); using the first one that reports a dmarc= result.");
        }

        Optional<AuthResultsSupport.MethodSegment> segment =
                authResults.isEmpty() ? Optional.empty() : AuthResultsSupport.findMethodSegment(authResults, "dmarc");

        if (segment.isPresent()) {
            AuthResultsSupport.MethodSegment seg = segment.get();
            result.setSource("Authentication-Results");
            result.setRawEvidence(seg.rawMatch());
            result.setResult(parseResultCode(seg.resultToken(), warnings));
            result.setVerificationMethod(VerificationMethod.REPORTED_BY_RECEIVING_SERVER);
            AuthResultsSupport.extractTag(seg.remainder(), "p").ifPresent(result::setPolicy);

            AuthResultsSupport.extractTag(seg.remainder(), "header.from").ifPresent(reportedFrom -> {
                if (result.getFromDomain() != null && !result.getFromDomain().equalsIgnoreCase(reportedFrom)) {
                    warnings.add("Authentication-Results header.from domain ('" + reportedFrom
                            + "') differs from the parsed From header domain ('" + result.getFromDomain() + "').");
                }
            });
        } else {
            result.setSource("NONE");
            result.setResult(DmarcResultCode.UNKNOWN);
            result.setVerificationMethod(VerificationMethod.NOT_PERFORMED);
            warnings.add("No Authentication-Results dmarc= evidence found.");
        }

        result.setSpfAligned(computeAlignment(result.getFromDomain(),
                spfResult == null ? null : spfResult.getDomain(), warnings, "SPF"));
        result.setDkimAligned(computeAlignment(result.getFromDomain(),
                dkimResult == null ? null : dkimResult.getSigningDomain(), warnings, "DKIM"));

        return result;
    }

    private AlignmentStatus computeAlignment(String fromDomain, String otherDomain, List<String> warnings, String label) {
        if (fromDomain == null || otherDomain == null) {
            return AlignmentStatus.UNKNOWN;
        }
        if (fromDomain.equalsIgnoreCase(otherDomain)) {
            return AlignmentStatus.ALIGNED;
        }
        warnings.add(label + " domain ('" + otherDomain + "') does not exactly match the From domain ('"
                + fromDomain + "'); only strict alignment is checked, so relaxed/subdomain alignment is not evaluated.");
        return AlignmentStatus.NOT_ALIGNED;
    }

    private DmarcResultCode parseResultCode(String token, List<String> warnings) {
        if (token == null) {
            return DmarcResultCode.UNKNOWN;
        }
        try {
            return DmarcResultCode.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.add("Unrecognized DMARC result token '" + token + "'.");
            return DmarcResultCode.UNKNOWN;
        }
    }
}
