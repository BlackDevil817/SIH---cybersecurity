package com.sih26106.emailintel.ioc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class UrlExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE);

    public List<String> extractUrls(String content) {
        List<String> urls = new ArrayList<>();
        if (content == null || content.isBlank()) return urls;
        Matcher m = URL_PATTERN.matcher(content);
        while (m.find()) {
            String url = m.group().trim();
            if (!urls.contains(url)) urls.add(url);
        }
        return urls;
    }

    public List<String> extractDomains(List<String> urls) {
        List<String> domains = new ArrayList<>();
        for (String url : urls) {
            try {
                String host = new URI(url).getHost();
                if (host != null && !host.isBlank()) {
                    String lower = host.toLowerCase();
                    if (!domains.contains(lower)) domains.add(lower);
                }
            } catch (Exception ex) {
                log.debug("UrlExtractor: cannot parse host from '{}'", url);
            }
        }
        return domains;
    }

    public List<String> extractDomainsFromContent(String content) {
        return extractDomains(extractUrls(content));
    }
}