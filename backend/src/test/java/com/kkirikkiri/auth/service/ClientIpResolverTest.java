package com.kkirikkiri.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test void ignoresUntrustedForwardedHeadersAndNormalizesIpv6() {
        var request=new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1"); request.addHeader("X-Forwarded-For","203.0.113.1");
        assertEquals("192.0.2.1",new ClientIpResolver("").resolve(request));
        request.setRemoteAddr("2001:db8::1");
        assertEquals("2001:db8:0:0:0:0:0:1",new ClientIpResolver("").resolve(request));
    }
    @Test void walksTrustedProxyChainFromTheRightAndIgnoresSpoofedLeftmostHop() {
        var request=new MockHttpServletRequest(); request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For","192.0.2.99, 203.0.113.1, 10.0.0.2");
        assertEquals("203.0.113.1",new ClientIpResolver("10.0.0.0/24").resolve(request));
    }
    @Test void malformedOrHostnameForwardingFallsBackToPeer() {
        var request=new MockHttpServletRequest(); request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For","malicious.example");
        assertEquals("10.0.0.1",new ClientIpResolver("10.0.0.0/24").resolve(request));
    }
}
