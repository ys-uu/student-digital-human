package com.edu.aitutor.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpUtilTest {

    @Test
    void prefersRealIpSetByNginx() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.17.0.1");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.2, 203.0.113.9");

        assertEquals("203.0.113.9", ClientIpUtil.resolve(request));
    }

    @Test
    void fallsBackToFirstValidForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.17.0.1");
        request.addHeader("X-Forwarded-For", "unknown, 198.51.100.7, 10.0.0.1");

        assertEquals("198.51.100.7", ClientIpUtil.resolve(request));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");

        assertEquals("192.168.1.20", ClientIpUtil.resolve(request));
    }

    @Test
    void ignoresSpoofedHeadersFromPublicClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Real-IP", "10.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.2");

        assertEquals("203.0.113.50", ClientIpUtil.resolve(request));
    }

    @Test
    void returnsUnknownWhenNothingIsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("not-an-ip");

        assertEquals("unknown", ClientIpUtil.resolve(request));
    }
}