package com.sih26106.emailintel.model;

/**
 * Classification of an IP address found in a Received header.
 * Only PUBLIC addresses are ever sent to GeolocationService.
 */
public enum IpClassification {
    PUBLIC,
    PRIVATE,
    LOOPBACK,
    LINK_LOCAL,
    MULTICAST,
    INVALID,
    UNKNOWN
}
