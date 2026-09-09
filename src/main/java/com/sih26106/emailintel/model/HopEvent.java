package com.sih26106.emailintel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "hop_events")
public class HopEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int hopOrder;
    private String ip;
    private String hostname;
    private Instant timestamp;
    private String geoCountry;
    private String geoCountryCode;
    private String geoCity;
    private String geoRegion;
    private Double geoLat;
    private Double geoLon;
    private String geoTimezone;
    private String geoOrg;
    private String geoAsn;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_analysis_id")
    private EmailAnalysis emailAnalysis;
}
