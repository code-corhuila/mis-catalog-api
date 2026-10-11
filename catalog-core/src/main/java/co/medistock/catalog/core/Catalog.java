package co.medistock.catalog.core;

import java.util.UUID;

public final class Catalog {
    private final Medications medications;

    public Catalog(Medications medications) { this.medications = medications; }

    public Medications.Creation create(UUID actor, String key, NewMedication request) {
        if (actor == null) throw CatalogFailure.invalid("actor");
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw CatalogFailure.invalid("Idempotency-Key");
        }
        return medications.create(actor, key, request);
    }

    public Medication find(String id) {
        return medications.find(identifier(id));
    }

    private UUID identifier(String id) {
        UUID parsed;
        try {
            if (id == null) throw new IllegalArgumentException();
            parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw CatalogFailure.invalid("id"); }
        return parsed;
    }

    public Medications.Versioned read(String id) { return medications.read(identifier(id)); }

    public Medications.Versioned update(UUID actor, String id, String match, NewMedication request) {
        if (actor == null) throw CatalogFailure.invalid("actor");
        if (request == null) throw CatalogFailure.invalid("body");
        return medications.update(actor, identifier(id), version(match), request);
    }

    public Medications.Versioned deactivate(UUID actor, String id, String match) {
        if (actor == null) throw CatalogFailure.invalid("actor");
        return medications.deactivate(actor, identifier(id), version(match));
    }

    private long version(String match) {
        if (match == null) throw new CatalogFailure(428, "PRECONDITION_REQUIRED", "Version requerida.", "If-Match");
        if (!match.matches("\"(0|[1-9][0-9]{0,18})\"")) throw CatalogFailure.invalid("If-Match");
        try { return Long.parseLong(match.substring(1, match.length() - 1)); }
        catch (NumberFormatException e) { throw CatalogFailure.invalid("If-Match"); }
    }

    public Medications.Listing list(int page, int limit) {
        if (page < 1) throw CatalogFailure.invalid("page");
        if (limit < 1 || limit > 100) throw CatalogFailure.invalid("limit");
        return medications.list(page, limit);
    }
}
