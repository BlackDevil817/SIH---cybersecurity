package com.sih26106.emailintel.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One reconstructed hop in the email's delivery path, in chronological order
 * (index 0 = origin, last index = final receiving server). Populated by
 * HopChainBuilder (Phase 3).
 */
public class HopEvent {

    private int hopNumber;
    private String sourceHostname;
    private String sourceIp;
    private String destinationHostname;
    private OffsetDateTime timestamp;
    private String protocol;

    /** PUBLIC/PRIVATE/LOOPBACK/LINK_LOCAL/MULTICAST/INVALID/UNKNOWN - see IpClassification. */
    private IpClassification ipClassification;

    private String country;
    private String countryCode;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String asn;
    private String organization;

    /** Provenance of the geo fields above, e.g. "MOCK", "UNAVAILABLE", or "SKIPPED_NON_PUBLIC"
     *  when the source IP wasn't PUBLIC and was never sent to GeolocationService. Null if no
     *  source IP was found at all. Never treat populated geo fields as verified without
     *  checking this. */
    private String geoSource;

    /** The original, unmodified "Received:" header text this hop was derived from. */
    private String rawReceivedHeader;

    /** 0.0-1.0 confidence that this hop was parsed/ordered correctly. */
    private double parsingConfidence = 1.0;

    private List<String> warnings = new ArrayList<>();

    public int getHopNumber() {
        return hopNumber;
    }

    public void setHopNumber(int hopNumber) {
        this.hopNumber = hopNumber;
    }

    public String getSourceHostname() {
        return sourceHostname;
    }

    public void setSourceHostname(String sourceHostname) {
        this.sourceHostname = sourceHostname;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getDestinationHostname() {
        return destinationHostname;
    }

    public void setDestinationHostname(String destinationHostname) {
        this.destinationHostname = destinationHostname;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public IpClassification getIpClassification() {
        return ipClassification;
    }

    public void setIpClassification(IpClassification ipClassification) {
        this.ipClassification = ipClassification;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getGeoSource() {
        return geoSource;
    }

    public void setGeoSource(String geoSource) {
        this.geoSource = geoSource;
    }

    public String getRawReceivedHeader() {
        return rawReceivedHeader;
    }

    public void setRawReceivedHeader(String rawReceivedHeader) {
        this.rawReceivedHeader = rawReceivedHeader;
    }

    public double getParsingConfidence() {
        return parsingConfidence;
    }

    public void setParsingConfidence(double parsingConfidence) {
        this.parsingConfidence = parsingConfidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
