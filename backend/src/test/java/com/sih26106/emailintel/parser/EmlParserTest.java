package com.sih26106.emailintel.parser;

import com.sih26106.emailintel.exception.UnsupportedEmailFormatException;
import com.sih26106.emailintel.model.ParsedEmail;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class EmlParserTest {

    private final EmlParser emlParser = new EmlParser(new HeaderExtractor());

    @Test
    void parsesTwoHopEmailWithAllCoreHeaders() throws Exception {
        try (InputStream is = resource("sample-2hop.eml")) {
            ParsedEmail parsed = emlParser.parse(is, "sample-2hop.eml");

            assertNotNull(parsed.getHeaders());
            assertEquals("Quarterly report attached", parsed.getHeaders().getSubject());
            assertEquals("<CAExample1234567890@example.net>", parsed.getHeaders().getMessageId());

            // Multiple Received headers must be preserved, in source order (top-of-file first).
            assertEquals(2, parsed.getHeaders().getReceived().size());
            assertTrue(parsed.getHeaders().getReceived().get(0).contains("mail.example.com"));
            assertTrue(parsed.getHeaders().getReceived().get(1).contains("smtp.example.net"));

            assertEquals(1, parsed.getHeaders().getAuthenticationResults().size());
            assertEquals(1, parsed.getHeaders().getDkimSignatures().size());
            assertEquals(1, parsed.getHeaders().getReceivedSpf().size());

            assertNotNull(parsed.getHeaders().getParsedDate());

            assertTrue(parsed.isMultipart());

            // One PDF attachment declared with Content-Disposition: attachment
            assertEquals(1, parsed.getAttachments().size());
            assertEquals("report.pdf", parsed.getAttachments().get(0).getFilename());
        }
    }

    @Test
    void rejectsCompletelyUnparseableContentAsUnsupportedFormat() throws Exception {
        try (InputStream is = resource("malformed.eml")) {
            assertThrows(UnsupportedEmailFormatException.class, () -> emlParser.parse(is, "malformed.eml"));
        }
    }

    @Test
    void neverThrowsOnEmptyButStructurallyValidHeaders() throws Exception {
        String minimal = "Subject: hello\r\n\r\nbody\r\n";
        try (InputStream is = new java.io.ByteArrayInputStream(minimal.getBytes())) {
            ParsedEmail parsed = emlParser.parse(is, "minimal.eml");
            assertEquals("hello", parsed.getHeaders().getSubject());
            assertTrue(parsed.getHeaders().getReceived().isEmpty());
        }
    }

    private InputStream resource(String name) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("sample-emails/" + name);
        assertNotNull(is, "Test fixture not found: " + name);
        return is;
    }
}
