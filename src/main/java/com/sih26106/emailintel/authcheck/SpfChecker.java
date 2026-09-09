package com.sih26106.emailintel.authcheck;
import org.springframework.stereotype.Component;

@Component
public class SpfChecker {
    public String check(String header) {
        if (header == null || header.isBlank()) return "NONE";
        String l = header.toLowerCase();
        if (l.contains("pass"))     return "PASS";
        if (l.contains("softfail")) return "SOFTFAIL";
        if (l.contains("fail"))     return "FAIL";
        if (l.contains("neutral"))  return "NEUTRAL";
        return "NONE";
    }
}