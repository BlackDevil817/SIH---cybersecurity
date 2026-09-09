package com.sih26106.emailintel.parser;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ReceivedHeaderAnalyzer {
    public List<String> analyze(String[] received) {
        if (received == null || received.length == 0)
            return List.of("No Received headers — suspicious");
        return List.of();
    }
    public boolean hasAnomalies(String[] received) {
        return !analyze(received).isEmpty();
    }
}