package com.sih26106.emailintel.model;

/**
 * Whether a domain (SPF envelope-from domain, or DKIM d= signing domain) matches
 * the message's visible From domain, as required for DMARC alignment.
 *
 * NOTE: only strict (exact-match) alignment is evaluated in this module - relaxed
 * alignment (matching organizational/subdomain) is a known, documented limitation.
 */
public enum AlignmentStatus {
    ALIGNED,
    NOT_ALIGNED,
    UNKNOWN
}
