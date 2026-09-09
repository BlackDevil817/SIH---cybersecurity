package com.sih26106.emailintel.fingerprint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderFingerprint {

    private String originatingIp;
    private String asn;
    private String asnOrganization;
    private String ipRange;

    private String dkimDomain;
    private String dkimSelector;
    private String spfRecord;
    private String dmarcPolicy;

    private String senderDomain;
    private String replyToDomain;
    private String returnPathDomain;
    private String fromDomain;

    @Builder.Default
    private List<String> urlDomains = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> features = new HashMap<>();
}