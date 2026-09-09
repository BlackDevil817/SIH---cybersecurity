package com.sih26106.emailintel.ioc;

import com.sih26106.emailintel.model.Ioc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class IocFormatterTest {

    IocFormatter fmt;
    @BeforeEach void setup() { fmt = new IocFormatter(); }

    @Test void testGrouped_multipleTypes() {
        var r = fmt.formatGrouped(List.of(
                ioc("IP","8.8.8.8"), ioc("DOMAIN","evil.com"),
                ioc("EMAIL","a@b.com"), ioc("URL","http://evil.com")));
        assertThat(r).containsKeys("IP","DOMAIN","EMAIL","URL");
    }

    @Test void testDeduplication() {
        var r = fmt.formatGrouped(List.of(ioc("IP","1.1.1.1"), ioc("IP","1.1.1.1"), ioc("IP","1.1.1.1")));
        assertThat(r.get("IP")).hasSize(1);
    }

    @Test void testNullValues() {
        var r = fmt.formatGrouped(Arrays.asList(ioc("IP",null), ioc(null,"v"), null));
        assertThat(r).isEmpty();
    }

    @Test void testEmptyList() { assertThat(fmt.formatGrouped(List.of())).isEmpty(); }
    @Test void testNullList()  { assertThat(fmt.formatGrouped(null)).isEmpty(); }

    @Test void testDomainNormalized() {
        var r = fmt.formatGrouped(List.of(ioc("DOMAIN","EVIL.COM")));
        assertThat(r.get("DOMAIN")).contains("evil.com");
    }

    @Test void testEmailNormalized() {
        var r = fmt.formatGrouped(List.of(ioc("EMAIL","A@B.COM")));
        assertThat(r.get("EMAIL")).contains("a@b.com");
    }

    @Test void testGetByType() {
        var iocs = List.of(ioc("IP","1.1.1.1"), ioc("IP","2.2.2.2"), ioc("DOMAIN","x.com"));
        assertThat(fmt.getByType(iocs,"IP")).hasSize(2);
        assertThat(fmt.getByType(iocs,"DOMAIN")).containsExactly("x.com");
    }

    @Test void testFormatFlat() {
        var r = fmt.formatFlat(List.of(ioc("IP","8.8.8.8")));
        assertThat(r).anyMatch(s -> s.contains("[IP]") && s.contains("8.8.8.8"));
    }

    private Ioc ioc(String t, String v) { return Ioc.builder().type(t).value(v).build(); }
}