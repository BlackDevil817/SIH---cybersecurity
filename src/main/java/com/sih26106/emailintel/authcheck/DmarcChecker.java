package com.sih26106.emailintel.authcheck;
import org.springframework.stereotype.Component;

@Component
public class DmarcChecker {
    public String check(String authResultsHeader) {
        if (authResultsHeader == null || authResultsHeader.isBlank()) return "NONE";
        String l = authResultsHeader.toLowerCase();
        if (!l.contains("dmarc")) return "NONE";
        if (l.contains("pass"))   return "PASS";
        if (l.contains("fail"))   return "FAIL";
        return "UNKNOWN";
    }
}