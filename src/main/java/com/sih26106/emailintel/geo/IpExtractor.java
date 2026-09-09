package com.sih26106.emailintel.geo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IpExtractor {

    private static final Pattern IPV4 = Pattern.compile(
            "\\b((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");

    /**
     * Extracts originating (sender) IP from Received headers array.
     * The last Received header in the array is the first/earliest hop = originating server.
     */
    public String extractOriginatingIp(String[] receivedHeaders) {
        if (receivedHeaders == null || receivedHeaders.length == 0) return null;
        // Last element = earliest hop = sender
        String last = receivedHeaders[receivedHeaders.length - 1];
        return extractFirstIp(last);
    }

    public String extractFirstIp(String line) {
        if (line == null) return null;
        Matcher m = IPV4.matcher(line);
        return m.find() ? m.group() : null;
    }
}