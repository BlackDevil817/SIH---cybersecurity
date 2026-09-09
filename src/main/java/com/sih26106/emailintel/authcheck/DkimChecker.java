package com.sih26106.emailintel.authcheck;
import org.springframework.stereotype.Component;

@Component
public class DkimChecker {
    public String check(String header) {
        if (header == null || header.isBlank()) return "NONE";
        String l = header.toLowerCase();
        if (l.contains("pass")) return "PASS";
        if (l.contains("fail")) return "FAIL";
        return header.contains("=") ? "PRESENT" : "NONE";
    }
}