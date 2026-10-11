package co.medistock.catalog;

import co.medistock.catalog.adapters.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.core.JacksonException;
import static org.junit.jupiter.api.Assertions.*;

class HttpConfigurationTest {
    @Test void rejectsOversizedUnknownLengthPostAndPut() throws Exception {
        for (String method : new String[]{"POST", "PUT"}) {
            var request = new MockHttpServletRequest(method, "/api/v1/medications") {
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setContent(new byte[65537]);
            var response = new MockHttpServletResponse();
            new RequestContext(new Errors()).doFilter(request, response, (a, b) -> fail("No debe llegar al controlador"));
            assertEquals(413, response.getStatus());
        }
    }

    @Test void strictJsonRejectsUnknownFieldsAndNontextCode() {
        var mapper = new HttpConfiguration().jsonMapper();
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"unknown\":true}", MedicationApi.Request.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"code\":7}", MedicationApi.Request.class));
    }

    @Test void invalidCorrelationStopsBeforeController() throws Exception {
        var request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Correlation-Id", "invalid correlation");
        var response = new MockHttpServletResponse();
        new RequestContext(new Errors()).doFilter(request, response, (a, b) -> fail("Correlacion invalida"));
        assertEquals(400, response.getStatus());
        assertNotNull(response.getHeader("X-Correlation-Id"));
    }

    @Test void validCorrelationIsPropagatedAndMdcCleared() throws Exception {
        var request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Correlation-Id", "server-test");
        var response = new MockHttpServletResponse();
        new RequestContext(new Errors()).doFilter(request, response,
            (a, b) -> assertEquals("server-test", a.getAttribute("traceId")));
        assertEquals("server-test", response.getHeader("X-Correlation-Id"));
        assertNull(org.slf4j.MDC.get("traceId"));
    }
}
