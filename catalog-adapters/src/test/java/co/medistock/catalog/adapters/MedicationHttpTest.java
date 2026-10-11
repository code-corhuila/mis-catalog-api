package co.medistock.catalog.adapters;

import co.medistock.catalog.core.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MedicationHttpTest {
    private final Medications store = mock(Medications.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
    private final Medication value = new Medication(id, "MED-1", "Nombre", "Principio",
        "Tableta", "10 mg", "Unidad", true, Instant.parse("2026-01-01T00:00:00Z"));
    private final NewMedication request = new NewMedication("MED-1", "Nombre", "Principio", "Tableta", "10 mg", "Unidad");
    private final String body = """
        {"code":" med-1 ","name":"Nombre","activeIngredient":"Principio",
        "pharmaceuticalForm":"Tableta","concentration":"10 mg","unitOfMeasure":"Unidad"}
        """;
    private MockMvc http;

    @BeforeEach void setup() {
        // Principal sintetico: estos tests no validan firmas, filtros ni roles.
        var jwt = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject(actor.toString()).build();
        http = MockMvcBuilders.standaloneSetup(new MedicationApi(store, jdbc))
            .setControllerAdvice(new Errors()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == Jwt.class; }
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                        NativeWebRequest web, WebDataBinderFactory binder) { return jwt; }
            }).build();
    }

    @Test void createAndReplayReturnLocationAndDifferentStatuses() throws Exception {
        when(store.create(actor, "request-123", request)).thenReturn(new Medications.Creation(value, true), new Medications.Creation(value, false));
        http.perform(post("/api/v1/medications").header("Idempotency-Key", "request-123")
            .contentType("application/json").content(body)).andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/medications/" + id)).andExpect(jsonPath("$.code").value("MED-1"));
        http.perform(post("/api/v1/medications").header("Idempotency-Key", "request-123")
            .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(store, times(2)).create(actor, "request-123", request);
    }

    @Test void readReturnsStrongEtagWithoutAddingVersionToJson() throws Exception {
        when(store.read(id)).thenReturn(new Medications.Versioned(value, 3));
        http.perform(get("/api/v1/medications/" + id)).andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"3\"")).andExpect(jsonPath("$.version").doesNotExist())
            .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }

    @Test void updateForwardsNormalizedBodyAndVersion() throws Exception {
        when(store.update(actor, id, 3, request)).thenReturn(new Medications.Versioned(value, 4));
        http.perform(put("/api/v1/medications/" + id).header("If-Match", "\"3\"")
            .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"4\""));
        verify(store).update(actor, id, 3, request);
    }

    @Test void missingVersionReturnsCorrelated428BeforePersistence() throws Exception {
        http.perform(put("/api/v1/medications/" + id).contentType("application/json").content(body)
            .requestAttr("traceId", "http-test")).andExpect(status().is(428))
            .andExpect(jsonPath("$.error").value("PRECONDITION_REQUIRED")).andExpect(jsonPath("$.traceId").value("http-test"));
        verifyNoInteractions(store);
    }

    @Test void deactivateRejectsNonemptyBody() throws Exception {
        http.perform(post("/api/v1/medications/" + id + "/deactivate").header("If-Match", "\"0\"")
            .contentType("application/json").content("{\"active\":true}")).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

    @Test void deactivateAllowsAbsentOrEmptyBody() throws Exception {
        var inactive = new Medication(id, value.code(), value.name(), value.activeIngredient(),
            value.pharmaceuticalForm(), value.concentration(), value.unitOfMeasure(), false, value.createdAt());
        when(store.deactivate(actor, id, 0)).thenReturn(new Medications.Versioned(inactive, 1));
        http.perform(post("/api/v1/medications/" + id + "/deactivate").header("If-Match", "\"0\""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        http.perform(post("/api/v1/medications/" + id + "/deactivate").header("If-Match", "\"0\"")
            .contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
    }

    @Test void malformedJsonAndInvalidPaginationReturn400() throws Exception {
        http.perform(post("/api/v1/medications").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/medications").param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

    @Test void unavailableDatabaseDoesNotExposeExceptionDetails() throws Exception {
        when(store.read(id)).thenThrow(new DataAccessResourceFailureException("private-db-details"));
        http.perform(get("/api/v1/medications/" + id).requestAttr("traceId", "http-test"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Dependencia no disponible."));
    }
}
