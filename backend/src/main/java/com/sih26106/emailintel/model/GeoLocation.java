package com.sih26106.emailintel.model;

/**
 * Structured geographic information for a single IP address.
 *
 * IMPORTANT: `source` always indicates provenance and must never be misrepresented -
 * e.g. "MOCK" for the SIH MVP's deterministic demo data, or "UNAVAILABLE" when nothing
 * could be determined. All other fields are null when unavailable - never fabricated.
 */
public class GeoLocation {

    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String timezone;

    /** Provenance of this data, e.g. "MOCK" or "UNAVAILABLE". Never claims to be verified
     *  real-world intelligence unless a real provider-backed implementation sets otherwise. */
    private String source;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
