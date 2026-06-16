package com.obank.booking.web.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class RateLimitFilterTest {

    private static final int GLOBAL_CAP = 10;
    private static final int PER_IP_CAP = 3;

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(GLOBAL_CAP, PER_IP_CAP);
    }

    @Test
    void allowsRequest_withinLimits() throws Exception {
        var req = holdRequest("10.0.0.1");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        assertThat(chain.getRequest()).as("filter chain must be invoked").isNotNull();
        assertThat(res.getHeader("X-Rate-Limit-Remaining")).isNotNull();
    }

    @Test
    void rejects_whenPerIpLimitExceeded() throws Exception {
        String ip = "10.0.0.2";
        exhaustPerIpBucket(ip);

        var res = new MockHttpServletResponse();
        filter.doFilter(holdRequest(ip), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getContentAsString()).contains("urn:booking:rate-limit");
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        exhaustPerIpBucket("10.0.1.1");

        var res = new MockHttpServletResponse();
        filter.doFilter(holdRequest("10.0.1.2"), res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    @Test
    void rejects_whenGlobalLimitExceeded() throws Exception {
        for (int i = 0; i < GLOBAL_CAP; i++) {
            filter.doFilter(holdRequest("192.168." + i + ".1"), new MockHttpServletResponse(), new MockFilterChain());
        }

        var res = new MockHttpServletResponse();
        filter.doFilter(holdRequest("192.168.99.99"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void usesXRealIp_notXForwardedFor() throws Exception {
        String realIp = "10.0.2.1";
        String forgedIp = "10.0.2.2";

        exhaustPerIpBucket(realIp);

        var req = new MockHttpServletRequest("POST", "/api/bookings/hold");
        req.addHeader("X-Real-IP", realIp);
        req.addHeader("X-Forwarded-For", forgedIp);
        var res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus())
                .as("X-Forwarded-For bypass must be rejected — X-Real-IP must be used")
                .isEqualTo(429);
    }

    @Test
    void fallsBackToRemoteAddr_whenNoXRealIpHeader() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/bookings/hold");
        req.setRemoteAddr("10.0.3.1");
        var res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    @Test
    void resolveClientIp_returnsXRealIp_whenPresent() {
        var req = new MockHttpServletRequest();
        req.addHeader("X-Real-IP", "  1.2.3.4  ");

        assertThat(filter.resolveClientIp(req)).isEqualTo("1.2.3.4");
    }

    @Test
    void resolveClientIp_returnsRemoteAddr_whenXRealIpAbsent() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("5.6.7.8");

        assertThat(filter.resolveClientIp(req)).isEqualTo("5.6.7.8");
    }

    @Test
    void skipsActuatorEndpoints() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).as("filter must be bypassed for actuator").isNotNull();
        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    private MockHttpServletRequest holdRequest(String realIp) {
        var req = new MockHttpServletRequest("POST", "/api/bookings/hold");
        req.addHeader("X-Real-IP", realIp);
        return req;
    }

    private void exhaustPerIpBucket(String ip) throws ServletException, IOException {
        for (int i = 0; i < PER_IP_CAP; i++) {
            filter.doFilter(holdRequest(ip), new MockHttpServletResponse(), new MockFilterChain());
        }
    }
}
