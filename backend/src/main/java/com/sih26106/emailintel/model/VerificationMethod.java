package com.sih26106.emailintel.model;

/**
 * Describes HOW an authentication result (SPF/DKIM/DMARC) was obtained.
 *
 * This module performs forensic analysis of an ALREADY RECEIVED email. It does not
 * perform its own DNS lookups (SPF) or cryptographic signature verification (DKIM).
 * Every result is therefore either:
 *  - what the receiving mail server already reported in its headers, or
 *  - nothing at all, if no such evidence exists in the email.
 */
public enum VerificationMethod {

    /** The result was parsed from evidence the receiving server already recorded
     *  (Authentication-Results, Received-SPF, DKIM-Signature). Not re-verified here. */
    REPORTED_BY_RECEIVING_SERVER,

    /** Reserved for a future phase that performs real DNS/SPF or cryptographic DKIM
     *  verification. Not used by this module today. */
    INDEPENDENTLY_VERIFIED,

    /** No independent verification was performed AND no reported evidence was found either.
     *  The associated result field is UNKNOWN/NONE, not a confirmed fact. */
    NOT_PERFORMED
}
