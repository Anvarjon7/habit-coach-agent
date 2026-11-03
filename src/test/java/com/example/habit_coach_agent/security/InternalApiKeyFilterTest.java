package com.example.habit_coach_agent.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class InternalApiKeyFilterTest {
    private static final String KEY = "test-internal-key";

    @Test void correctKeyAllowsInternalRequest() throws Exception {
        var request = request("/api/participants");
        request.addHeader(InternalApiKeyFilter.HEADER, KEY);
        assertTrue(filter(KEY,request).passed());
    }

    @Test void missingAndIncorrectKeysAreRejected() throws Exception {
        var missing = filter(KEY,request("/api/participants"));
        assertEquals(401,missing.response().getStatus()); assertFalse(missing.passed());
        var request = request("/api/participants"); request.addHeader(InternalApiKeyFilter.HEADER,"incorrect");
        var wrong = filter(KEY,request);
        assertEquals(401,wrong.response().getStatus());
        assertEquals("{\"error\":\"Unauthorized\"}", wrong.response().getContentAsString());
    }

    @Test void unsetOrBlankConfigurationFailsClosed() throws Exception {
        var request = request("/api"); request.addHeader(InternalApiKeyFilter.HEADER,KEY);
        assertFalse(filter("",request).passed());
        assertFalse(filter("  ",request).passed());
    }

    @Test void duplicatedHeadersAreRejected() throws Exception {
        var request = request("/api/participants");
        request.addHeader(InternalApiKeyFilter.HEADER,KEY);
        request.addHeader(InternalApiKeyFilter.HEADER,KEY);
        assertFalse(filter(KEY,request).passed());
    }

    @Test void servletContextAndMatrixParametersCannotBypassAuthentication() throws Exception {
        var request = request("/coach/api;parameter/participants"); request.setContextPath("/coach");
        assertFalse(filter(KEY,request).passed());
        var decoded = request("/api%3Bparameter/participants");
        decoded.setServletPath("/api;parameter/participants");
        assertFalse(filter(KEY,decoded).passed());
        assertFalse(filter(KEY,request("//api//participants")).passed());
    }

    @Test void intendedHealthChecksArePublic() throws Exception {
        for (String path : new String[]{"/actuator/health","/actuator/health/liveness","/actuator/health/readiness"}) {
            assertTrue(filter("",request(path)).passed());
        }
    }

    @Test void otherActuatorEndpointsFailClosedEvenWithInternalKey() throws Exception {
        for (String path : new String[]{"/actuator","/actuator/env","/actuator/metrics","/actuator/health/secret"}) {
            var request = request(path); request.addHeader(InternalApiKeyFilter.HEADER,KEY);
            var result = filter(KEY,request);
            assertFalse(result.passed()); assertEquals(404,result.response().getStatus());
        }
    }

    @Test void nonReadHealthRequestsDoNotPass() throws Exception {
        var request = request("/actuator/health"); request.setMethod("POST");
        assertFalse(filter("",request).passed());
    }

    @Test void webhookRemainsAvailableForItsOwnAuthentication() throws Exception {
        assertTrue(filter("",request("/telegram/webhook")).passed());
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET",path);
    }
    private static Result filter(String configured, MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new InternalApiKeyFilter(configured).doFilter(request,response,chain);
        return new Result(response, chain.getRequest() != null);
    }
    private record Result(MockHttpServletResponse response, boolean passed) {}
}
