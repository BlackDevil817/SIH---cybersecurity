package com.sih26106.emailintel.ioc;

import com.sih26106.emailintel.model.Ioc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class IocFormatter {

    public Map<String, List<String>> formatGrouped(List<Ioc> iocs) {
        if (iocs == null || iocs.isEmpty()) return Collections.emptyMap();

        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (Ioc ioc : iocs) {
            if (ioc == null) continue;
            String type  = ioc.getType();
            String value = ioc.getValue();
            if (type == null || type.isBlank() || value == null || value.isBlank()) continue;

            String normType  = type.trim().toUpperCase();
            String normValue = normalizeValue(normType, value);
            if (normValue == null || normValue.isBlank()) continue;

            grouped.computeIfAbsent(normType, k -> new ArrayList<>()).add(normValue);
        }

        // Deduplicate and sort each list
        grouped.replaceAll((type, values) ->
                values.stream().distinct().sorted().collect(Collectors.toList()));

        log.debug("IocFormatter: {} types from {} raw IOCs", grouped.size(), iocs.size());
        return grouped;
    }

    public List<String> formatFlat(List<Ioc> iocs) {
        if (iocs == null || iocs.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Ioc ioc : iocs) {
            if (ioc == null || ioc.getValue() == null || ioc.getValue().isBlank()) continue;
            String type  = ioc.getType() != null ? ioc.getType().trim().toUpperCase() : "UNKNOWN";
            String value = normalizeValue(type, ioc.getValue());
            if (value != null && !value.isBlank()) result.add("[" + type + "] " + value);
        }
        return result.stream().distinct().sorted().collect(Collectors.toList());
    }

    public List<String> getByType(List<Ioc> iocs, String type) {
        if (iocs == null || type == null) return Collections.emptyList();
        String target = type.trim().toUpperCase();
        List<String> result = new ArrayList<>();
        for (Ioc ioc : iocs) {
            if (ioc == null) continue;
            String iocType = ioc.getType() != null ? ioc.getType().trim().toUpperCase() : "";
            if (!target.equals(iocType)) continue;
            String val = normalizeValue(target, ioc.getValue());
            if (val != null && !val.isBlank()) result.add(val);
        }
        return result.stream().distinct().sorted().collect(Collectors.toList());
    }

    private String normalizeValue(String type, String value) {
        if (value == null) return null;
        String v = value.trim();
        return switch (type) {
            case "DOMAIN", "DKIM_DOMAIN", "EMAIL" -> v.toLowerCase();
            default -> v;
        };
    }
}