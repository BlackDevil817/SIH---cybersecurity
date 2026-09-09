package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.exception.EmailParsingException;
import com.sih26106.emailintel.model.EmailAnalysis;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmlParser {

    private final HeaderExtractor headerExtractor;

    public EmailAnalysis parse(InputStream stream, String fileName) {
        try {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage msg = new MimeMessage(session, stream);

            String from    = addr(msg.getFrom());
            String to      = addr(msg.getRecipients(Message.RecipientType.TO));
            String headers = allHeaders(msg);
            String[] received = msg.getHeader("Received");

            return EmailAnalysis.builder()
                    .fileName(fileName)
                    .subject(msg.getSubject())
                    .fromAddress(from)
                    .toAddress(to)
                    .messageId(msg.getMessageID())
                    .senderDomain(headerExtractor.extractDomain(from))
                    .replyToDomain(headerExtractor.extractReplyToDomain(msg))
                    .returnPathDomain(headerExtractor.extractReturnPathDomain(msg))
                    .dkimDomain(headerExtractor.extractDkimDomain(headers))
                    .dkimSelector(headerExtractor.extractDkimSelector(headers))
                    .senderIp(headerExtractor.extractOriginatingIp(received))
                    .receivedAt(msg.getSentDate() != null ? msg.getSentDate().toInstant() : Instant.now())
                    .analyzedAt(Instant.now())
                    .build();

        } catch (Exception ex) {
            throw new EmailParsingException("Failed to parse: " + fileName, ex);
        }
    }

    private String addr(Address[] a) {
        return (a != null && a.length > 0) ? a[0].toString() : null;
    }

    private String allHeaders(MimeMessage msg) {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<jakarta.mail.Header> en = msg.getAllHeaders();
            while (en.hasMoreElements()) {
                jakarta.mail.Header h = en.nextElement();
                sb.append(h.getName()).append(": ").append(h.getValue()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}