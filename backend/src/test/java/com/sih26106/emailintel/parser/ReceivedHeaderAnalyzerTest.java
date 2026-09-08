package com.sih26106.emailintel.parser;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReceivedHeaderAnalyzerTest {

    private final ReceivedHeaderAnalyzer analyzer = new ReceivedHeaderAnalyzer();

    @Test
    void parsesNormalReceivedHeaderCompletely() {
        String header = "from mail.example.com (mail.example.com [203.0.113.10]) "
                + "by mx.target.com with ESMTPS id abc123 for <victim@target.com>; "
                + "Tue, 1 Sep 2026 10:15:20 +0000";

        List<ParsedReceivedHeader> results = analyzer.parse(List.of(header));

        assertEquals(1, results.size());
        ParsedReceivedHeader parsed = results.get(0);

        assertEquals(header, parsed.getRawHeader());
        assertEquals("mail.example.com", parsed.getFromHost());
        assertTrue(parsed.getFromRaw().contains("203.0.113.10"));
        assertEquals("mx.target.com", parsed.getByHost());
        assertEquals("ESMTPS", parsed.getProtocol());
        assertEquals(OffsetDateTime.of(2026, 9, 1, 10, 15, 20, 0, ZoneOffset.UTC), parsed.getParsedTimestamp());
        assertTrue(parsed.getWarnings().isEmpty());
    }

    @Test
    void parsesMultipleHeadersPreservingOriginalOrderAndIndex() {
        List<String> headers = List.of(
                "from mail.example.com (mail.example.com [203.0.113.10]) by mx.target.com with ESMTPS; "
                        + "Tue, 1 Sep 2026 10:15:20 +0000",
                "from smtp.example.net (smtp.example.net [198.51.100.20]) by mail.example.com with ESMTP; "
                        + "Tue, 1 Sep 2026 10:10:00 +0000");

        List<ParsedReceivedHeader> results = analyzer.parse(headers);

        assertEquals(2, results.size());
        assertEquals(0, results.get(0).getOriginalIndex());
        assertEquals(1, results.get(1).getOriginalIndex());
        assertEquals("mail.example.com", results.get(0).getFromHost());
        assertEquals("smtp.example.net", results.get(1).getFromHost());
    }

    @Test
    void neverThrowsOnCompletelyMalformedHeaderAndPreservesRawText() {
        String garbage = "this is not a real received header at all, just noise 12345 ???";

        List<ParsedReceivedHeader> results = analyzer.parse(List.of(garbage));

        assertEquals(1, results.size());
        ParsedReceivedHeader parsed = results.get(0);
        assertEquals(garbage, parsed.getRawHeader());
        assertNull(parsed.getFromHost());
        assertNull(parsed.getByHost());
        assertNull(parsed.getParsedTimestamp());
        assertFalse(parsed.getWarnings().isEmpty());
    }

    @Test
    void missingByClauseAndTimestampProduceNullsNotGuesses() {
        String header = "from mail.example.com (mail.example.com [203.0.113.10])";

        List<ParsedReceivedHeader> results = analyzer.parse(List.of(header));
        ParsedReceivedHeader parsed = results.get(0);

        assertEquals("mail.example.com", parsed.getFromHost());
        assertNull(parsed.getByHost());
        assertNull(parsed.getParsedTimestamp());
        assertTrue(parsed.getWarnings().stream().anyMatch(w -> w.contains("'by' clause")));
        assertTrue(parsed.getWarnings().stream().anyMatch(w -> w.contains("timestamp")));
    }

    @Test
    void parsesFoldedMultilineHeaderCorrectly() {
        String folded = "from mail.example.com (mail.example.com [203.0.113.10])\r\n"
                + "\tby mx.target.com with ESMTPS id abc123;\r\n"
                + "\tTue, 1 Sep 2026 10:15:20 +0000";

        List<ParsedReceivedHeader> results = analyzer.parse(List.of(folded));
        ParsedReceivedHeader parsed = results.get(0);

        assertEquals("mail.example.com", parsed.getFromHost());
        assertEquals("mx.target.com", parsed.getByHost());
        assertNotNull(parsed.getParsedTimestamp());
    }
}
