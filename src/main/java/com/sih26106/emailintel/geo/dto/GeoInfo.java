package com.sih26106.emailintel.geo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoInfo {

    private String ip;
    private String status;        // "success" or "fail" from ip-api.com
    private String country;
    private String countryCode;
    private String region;
    private String regionName;
    private String city;
    private double lat;
    private double lon;
    private String timezone;
    private String isp;
    private String org;

    @JsonProperty("as")           // ip-api.com returns ASN in "as" field
    private String asn;           // e.g. "AS15169 Google LLC"

    private boolean valid;
    private boolean privateAddress;
    private String errorMessage;

    public static GeoInfo unavailable(String ip) {
        return GeoInfo.builder()
                .ip(ip)
                .valid(false)
                .privateAddress(false)
                .errorMessage("Geolocation unavailable")
                .build();
    }

    public static GeoInfo privateIp(String ip) {
        return GeoInfo.builder()
                .ip(ip)
                .valid(true)
                .privateAddress(true)
                .country("Private")
                .countryCode("XX")
                .errorMessage("Private/reserved IP address")
                .build();
    }
}