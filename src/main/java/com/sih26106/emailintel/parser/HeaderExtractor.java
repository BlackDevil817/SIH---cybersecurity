package com.sih26106.emailintel.parser;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class HeaderExtractor {

    private static final Pattern IP = Pattern.compile(
            "\\b((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");
    private static final Pattern DKIM_D  = Pattern.compile("d=([a-zA-Z0-9.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DKIM_S  = Pattern.compile("s=([a-zA-Z0-9._-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RET_PATH = Pattern.compile("<[^@]+@([^>]+)>");

    public String extractOriginatingIp(String[] received) {
        if (received == null || received.length == 0) return null;
        Matcher m = IP.matcher(received[received.length - 1]);
        return m.find() ? m.group() : null;
    }

    public String extractDomain(String email) {
        if (email == null || !email.contains("@")) return null;
        String[] p = email.replaceAll("[<>]", "").trim().split("@");
        return p.length == 2 ? p[1].toLowerCase().trim() : null;
    }

    public String extractDkimDomain(String headers) {
        if (headers == null) return null;
        Matcher m = DKIM_D.matcher(headers);
        return m.find() ? m.group(1).toLowerCase() : null;
    }

    public String extractDkimSelector(String headers) {
        if (headers == null) return null;
        Matcher m = DKIM_S.matcher(headers);
        return m.find() ? m.group(1) : null;
    }

    public String extractReplyToDomain(MimeMessage msg) {
        try {
            String[] h = msg.getHeader("Reply-To");
            return (h != null && h.length > 0) ? extractDomain(h[0]) : null;
        } catch (MessagingException e) { return null; }
    }

    public String extractReturnPathDomain(MimeMessage msg) {
        try {
            String[] h = msg.getHeader("Return-Path");
            if (h == null || h.length == 0) return null;
            Matcher m = RET_PATH.matcher(h[0]);
            return m.find() ? m.group(1).toLowerCase() : null;
        } catch (MessagingException e) { return null; }
    }
}