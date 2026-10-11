package co.medistock.catalog.adapters;

import co.medistock.catalog.core.*;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class MedicationApi {
    public record Request(String code, String name, String activeIngredient,
        String pharmaceuticalForm, String concentration, String unitOfMeasure) {
        NewMedication normalized() {
            return new NewMedication(code, name, activeIngredient, pharmaceuticalForm, concentration, unitOfMeasure);
        }
    }

    private final Catalog catalog;
    private final JdbcTemplate jdbc;

    public MedicationApi(Medications medications, JdbcTemplate jdbc) {
        catalog = new Catalog(medications); this.jdbc = jdbc;
    }

    @PostMapping("/api/v1/medications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Medication> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody Request request) {
        var result = catalog.create(UUID.fromString(jwt.getSubject()), key, request.normalized());
        return ResponseEntity.status(result.created() ? 201 : 200)
            .location(URI.create("/api/v1/medications/" + result.medication().id())).body(result.medication());
    }

    @GetMapping("/api/v1/medications")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY','SUPERVISOR','CONSULTATION')")
    public Medications.Listing list(@RequestParam(required = false) String page,
            @RequestParam(required = false) String limit) {
        return catalog.list(number(page, "page", 1), number(limit, "limit", 20));
    }

    private int number(String value, String field, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException e) { throw CatalogFailure.invalid(field); }
    }

    @GetMapping("/api/v1/medications/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY','SUPERVISOR','CONSULTATION')")
    public ResponseEntity<Medication> find(@PathVariable String id) { return response(catalog.read(id)); }

    @PutMapping("/api/v1/medications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Medication> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader(value="If-Match", required=false) String match, @RequestBody Request request) {
        return response(catalog.update(UUID.fromString(jwt.getSubject()), id, match, request.normalized()));
    }

    @PostMapping("/api/v1/medications/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Medication> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader(value="If-Match", required=false) String match,
            @RequestBody(required=false) Map<String,Object> body) {
        if (body != null && !body.isEmpty()) throw CatalogFailure.invalid("body");
        return response(catalog.deactivate(UUID.fromString(jwt.getSubject()), id, match));
    }

    private ResponseEntity<Medication> response(Medications.Versioned result) {
        return ResponseEntity.ok().eTag("\"" + result.version() + "\"").body(result.medication());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        jdbc.queryForObject("SELECT count(*) FROM catalog.medication WHERE false", Long.class);
        return Map.of("status", "UP");
    }
}
