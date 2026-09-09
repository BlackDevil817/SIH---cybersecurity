package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.SpfResult;
import com.sih26106.emailintel.model.SpfResultCode;
import com.sih26106.emailintel.model.VerificationMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets SPF evidence already reported by the receiving mail server.
 *
 * IMPORTANT: this class performs NO DNS lookups and does NOT independently verify SPF.
 * It only parses what the receiving server already recorded in the Authentication-Results
 * or (older-style) Received-SPF headers. See SpfResult.verificationMethod for exactly how
 * each result was obtained.
 *
 * Preference order when both headers are present: Authentication-Results is treated as the
 * canonical, modern source; Received-SPF is used only as a fallback.
 */
@Component
public class SpfChecker {

    private static final Pattern RECEIVED_SPF_LEADING_TOKEN = Pattern.compile("(?i)^\\s*([a-zA-Z]+)");
    // Matches both phrasings mail servers use in the Received-SPF comment:
    //   PASS-style:            "domain of X designates Y as permitted sender"
    //   FAIL/SOFTFAIL-style:   "domain of X does not designate Y as permitted sender"
    private static final Pattern RECEIVED_SPF_ENVELOPE_FROM =
            Pattern.compile("(?i)domain of\\s+([^\\s]+)\\s+(?:designates|does not designate)");

    public SpfResult check(EmailHeaders headers) {
        SpfResult result = new SpfResult();
        List<String> warnings = new ArrayList<>();
        result.setWarnings(warnings);

        List<String> authResults = headers.getAuthenticationResults();
        List<String> receivedSpf = headers.getReceivedSpf();

        if (authResults.size() > 1) {
            warnings.add("Multiple Authentication-Results headers present (" + authResults.size()
                    + "); using the first one that reports an spf= result.");
        }
        if (receivedSpf.size() > 1) {
            warnings.add("Multiple Received-SPF headers present (" + receivedSpf.size()
                    + "); using the first one.");
        }

        Optional<AuthResultsSupport.MethodSegment> segment =
                authResults.isEmpty() ? Optional.empty() : AuthResultsSupport.findMethodSegment(authResults, "spf");

        if (segment.isPresent()) {
            parseFromAuthenticationResults(segment.get(), result);
        } else if (!receivedSpf.isEmpty()) {
            parseFromReceivedSpf(receivedSpf.get(0), result, warnings);
        } else {
            result.setSource("NONE");
            result.setResult(SpfResultCode.UNKNOWN);
            result.setVerificationMethod(VerificationMethod.NOT_PERFORMED);
            warnings.add("No SPF evidence found in Authentication-Results or Received-SPF headers.");
        }

        return result;
    }

    private void parseFromAuthenticationResults(AuthResultsSupport.MethodSegment seg, SpfResult result) {
        result.setSource("Authentication-Results");
        result.setRawEvidence(seg.rawMatch());
        result.setResult(parseResultCode(seg.resultToken()));
        result.setVerificationMethod(VerificationMethod.REPORTED_BY_RECEIVING_SERVER);

        Optional<String> mailfromDomain = AuthResultsSupport.extractTag(seg.remainder(), "smtp.mailfrom")
                .flatMap(AuthResultsSupport::extractDomainFromAddress);
        Optional<String> heloDomain = AuthResultsSupport.extractTag(seg.remainder(), "smtp.helo");
        mailfromDomain.or(() -> heloDomain).ifPresent(result::setDomain);

        AuthResultsSupport.extractTag(seg.remainder(), "client-ip").ifPresent(result::setIp);
    }

    private void parseFromReceivedSpf(String rawHeader, SpfResult result, List<String> warnings) {
        String raw = AuthResultsSupport.normalize(rawHeader);
        result.setSource("Received-SPF");
        result.setRawEvidence(raw);
        result.setVerificationMethod(VerificationMethod.REPORTED_BY_RECEIVING_SERVER);

        Matcher tokenMatcher = RECEIVED_SPF_LEADING_TOKEN.matcher(raw);
        if (tokenMatcher.find()) {
            result.setResult(parseResultCode(tokenMatcher.group(1)));
        } else {
            result.setResult(SpfResultCode.UNKNOWN);
            warnings.add("Could not find a leading SPF result token in Received-SPF header.");
        }

        AuthResultsSupport.extractTag(raw, "client-ip").ifPresent(result::setIp);

        Matcher envelopeMatcher = RECEIVED_SPF_ENVELOPE_FROM.matcher(raw);
        if (envelopeMatcher.find()) {
            AuthResultsSupport.extractDomainFromAddress(envelopeMatcher.group(1)).ifPresent(result::setDomain);
        }
    }

    private SpfResultCode parseResultCode(String token) {
        if (token == null) {
            return SpfResultCode.UNKNOWN;
        }
        try {
            return SpfResultCode.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SpfResultCode.UNKNOWN;
        }
    }
}
