package com.sih26106.emailintel.authcheck;

import com.sih26106.emailintel.model.DkimResult;
import com.sih26106.emailintel.model.DkimResultCode;
import com.sih26106.emailintel.model.EmailHeaders;
import com.sih26106.emailintel.model.VerificationMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interprets DKIM evidence already present in the email:
 *  - the Authentication-Results dkim= verdict, as reported by the receiving server;
 *  - DKIM-Signature header metadata (d=, s=, a=, c=, h=, i=).
 *
 * IMPORTANT: this class does NOT perform cryptographic signature verification. A
 * DKIM-Signature header only proves the message claims to be signed by the given domain -
 * it says nothing about validity unless a dkim= verdict from a verifier is also present.
 * See DkimResult.verificationMethod.
 */
@Component
public class DkimChecker {

    public DkimResult check(EmailHeaders headers) {
        DkimResult result = new DkimResult();
        List<String> warnings = new ArrayList<>();
        result.setWarnings(warnings);

        List<String> signatures = headers.getDkimSignatures();
        List<String> authResults = headers.getAuthenticationResults();

        if (signatures.size() > 1) {
            warnings.add("Multiple DKIM-Signature headers present (" + signatures.size()
                    + "); using the first one for metadata.");
        }
        if (authResults.size() > 1) {
            warnings.add("Multiple Authentication-Results headers present (" + authResults.size()
                    + "); using the first one that reports a dkim= result.");
        }

        boolean hasSignatureMetadata = false;
        if (!signatures.isEmpty()) {
            String raw = AuthResultsSupport.normalize(signatures.get(0));
            result.setRawEvidence(raw);
            AuthResultsSupport.extractTag(raw, "d").ifPresent(result::setSigningDomain);
            AuthResultsSupport.extractTag(raw, "s").ifPresent(result::setSelector);
            AuthResultsSupport.extractTag(raw, "a").ifPresent(result::setAlgorithm);
            AuthResultsSupport.extractTag(raw, "c").ifPresent(result::setCanonicalization);
            AuthResultsSupport.extractTag(raw, "h").ifPresent(result::setHeaderList);
            AuthResultsSupport.extractTag(raw, "i").ifPresent(result::setIdentity);
            hasSignatureMetadata = true;
        }

        Optional<AuthResultsSupport.MethodSegment> segment =
                authResults.isEmpty() ? Optional.empty() : AuthResultsSupport.findMethodSegment(authResults, "dkim");

        if (segment.isPresent()) {
            AuthResultsSupport.MethodSegment seg = segment.get();
            result.setSource(hasSignatureMetadata ? "DKIM-Signature+Authentication-Results" : "Authentication-Results");
            result.setResult(parseResultCode(seg.resultToken()));
            result.setVerificationMethod(VerificationMethod.REPORTED_BY_RECEIVING_SERVER);

            // header.d in Authentication-Results is the verifier's own record of the signing
            // domain - only use it as a fallback if we didn't already get one from the raw signature.
            if (result.getSigningDomain() == null) {
                AuthResultsSupport.extractTag(seg.remainder(), "header.d").ifPresent(result::setSigningDomain);
            }
            if (result.getIdentity() == null) {
                AuthResultsSupport.extractTag(seg.remainder(), "header.i").ifPresent(result::setIdentity);
            }

        } else if (hasSignatureMetadata) {
            // Signature exists, but no verifier verdict was found - we genuinely don't know
            // whether it's valid, and this module does not check the cryptography itself.
            result.setSource("DKIM-Signature");
            result.setResult(DkimResultCode.UNKNOWN);
            result.setVerificationMethod(VerificationMethod.NOT_PERFORMED);
            warnings.add("DKIM-Signature header is present but no Authentication-Results dkim= verdict "
                    + "was found, and this module does not perform cryptographic verification itself.");
        } else {
            result.setSource("NONE");
            result.setResult(DkimResultCode.NONE);
            result.setVerificationMethod(VerificationMethod.NOT_PERFORMED);
            warnings.add("No DKIM-Signature header and no Authentication-Results dkim= evidence found.");
        }

        return result;
    }

    private DkimResultCode parseResultCode(String token) {
        if (token == null) {
            return DkimResultCode.UNKNOWN;
        }
        try {
            return DkimResultCode.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DkimResultCode.UNKNOWN;
        }
    }
}
