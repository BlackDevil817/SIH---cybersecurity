package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.model.EmailHeaders;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class HeaderExtractorTest {

    private final HeaderExtractor headerExtractor = new HeaderExtractor();

    @Test
    void preservesOrderAndMultiplicityOfReceivedHeaders() throws Exception {
        MimeMessage message = load("sample-2hop.eml");
        List<String> warnings = new ArrayList<>();

        EmailHeaders headers = headerExtractor.extract(message, warnings);

        assertEquals(2, headers.getReceived().size());
        // Source-order preserved: the header nearest the top of the file (most recent hop) first.
        assertTrue(headers.getReceived().get(0).contains("203.0.113.10"));
        assertTrue(headers.getReceived().get(1).contains("198.51.100.20"));
    }

    @Test
    void extractsAddressAndIdentityFields() throws Exception {
        MimeMessage message = load("sample-2hop.eml");
        List<String> warnings = new ArrayList<>();

        EmailHeaders headers = headerExtractor.extract(message, warnings);

        assertEquals(1, headers.getFrom().size());
        assertTrue(headers.getFrom().get(0).contains("alice@example.net"));

        assertEquals(1, headers.getTo().size());
        assertTrue(headers.getTo().get(0).contains("victim@target.com"));

        assertEquals(1, headers.getCc().size());
        assertEquals("<CAExample1234567890@example.net>", headers.getMessageId());
    }

    @Test
    void extractsAuthenticationRelatedHeadersWithoutOverwriting() throws Exception {
        MimeMessage message = load("sample-2hop.eml");
        List<String> warnings = new ArrayList<>();

        EmailHeaders headers = headerExtractor.extract(message, warnings);

        assertEquals(1, headers.getAuthenticationResults().size());
        assertTrue(headers.getAuthenticationResults().get(0).contains("spf=pass"));

        assertEquals(1, headers.getDkimSignatures().size());
        assertTrue(headers.getDkimSignatures().get(0).contains("d=example.net"));

        assertEquals(1, headers.getReceivedSpf().size());
        assertTrue(headers.getReceivedSpf().get(0).contains("pass"));
    }

    @Test
    void missingHeadersProduceEmptyListsNotNulls() throws Exception {
        String minimal = "Subject: no headers here\r\n\r\nbody\r\n";
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(minimal.getBytes()));
        List<String> warnings = new ArrayList<>();

        EmailHeaders headers = headerExtractor.extract(message, warnings);

        assertNotNull(headers.getReceived());
        assertTrue(headers.getReceived().isEmpty());
        assertNotNull(headers.getFrom());
        assertTrue(headers.getFrom().isEmpty());
        assertNull(headers.getParsedDate());
    }

    private MimeMessage load(String name) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-emails/" + name)) {
            assertNotNull(is, "Test fixture not found: " + name);
            return new MimeMessage(Session.getInstance(new Properties()), is);
        }
    }
}
