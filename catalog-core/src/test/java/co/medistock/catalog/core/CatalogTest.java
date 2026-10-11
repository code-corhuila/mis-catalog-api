package co.medistock.catalog.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    private final Catalog catalog = new Catalog(new Medications() {
        public Creation create(UUID actor, String key, NewMedication request) { return null; }
        public Medication find(UUID id) { return null; }
        public Listing list(int page, int limit) { return null; }
        public Versioned read(UUID id) { return null; }
        public Versioned update(UUID actor, UUID id, long version, NewMedication request) { return null; }
        public Versioned deactivate(UUID actor, UUID id, long version) { return null; }
    });

    private NewMedication request(String code, String name) {
        return new NewMedication(code, name, "Principio", "Tableta", "10 mg", "Unidad");
    }

    @Test void normalizesCodeAndBusinessText() {
        var result = request(" med-01 ", " Medicamento ");
        assertEquals("MED-01", result.code()); assertEquals("Medicamento", result.name());
    }

    @Test void rejectsMissingOrBlankText() {
        assertEquals("name", assertThrows(CatalogFailure.class, () -> request("ABC", null)).field());
        assertThrows(CatalogFailure.class, () -> request("ABC", "   "));
        assertThrows(CatalogFailure.class, () -> new NewMedication("ABC", "A", "B", "C", "D", null));
    }

    @Test void enforcesCodeAndTextLimits() {
        for (String code : new String[] {"A_1", "A/1", "A".repeat(41), "", "\u00df"}) {
            assertThrows(CatalogFailure.class, () -> request(code, "Nombre"));
        }
        assertThrows(CatalogFailure.class, () -> request("ABC", "x".repeat(161)));
        assertDoesNotThrow(() -> request("A".repeat(40), "x".repeat(160)));
    }

    @Test void validatesIdempotencyKeyWithoutTrimming() {
        var body = request("ABC", "Nombre"); var actor = UUID.randomUUID();
        for (String key : new String[] {null, "short", " key-1234", "key/1234", "x".repeat(129)}) {
            assertEquals("Idempotency-Key", assertThrows(CatalogFailure.class, () -> catalog.create(actor, key, body)).field());
        }
        assertDoesNotThrow(() -> catalog.create(actor, "key-1234", body));
    }

    @Test void boundsPagination() {
        assertThrows(CatalogFailure.class, () -> catalog.list(0, 20));
        assertThrows(CatalogFailure.class, () -> catalog.list(1, 0));
        assertThrows(CatalogFailure.class, () -> catalog.list(1, 101));
        assertDoesNotThrow(() -> catalog.list(1, 100));
    }

    @Test void acceptsOnlyCanonicalUuid() {
        assertThrows(CatalogFailure.class, () -> catalog.find("1-1-1-1-1"));
        assertThrows(CatalogFailure.class, () -> catalog.find("NOT-UUID"));
        assertThrows(CatalogFailure.class, () -> catalog.find("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"));
        assertDoesNotThrow(() -> catalog.find(UUID.randomUUID().toString()));
    }

    @Test void requiresStrongVersionForMaintenance() {
        var actor = UUID.randomUUID(); var id = UUID.randomUUID().toString();
        assertEquals(428, assertThrows(CatalogFailure.class, () -> catalog.deactivate(actor, id, null)).status());
        for (String match : new String[]{"*", "0", "W/\"0\"", "\"-1\"", "\"00\"", "\"0\",\"1\"", "\"9999999999999999999\""}) {
            assertEquals(400, assertThrows(CatalogFailure.class, () -> catalog.deactivate(actor, id, match)).status());
        }
        assertDoesNotThrow(() -> catalog.update(actor, id, "\"0\"", request("ABC", "Nombre")));
        assertThrows(CatalogFailure.class, () -> catalog.update(null, id, "\"0\"", request("ABC", "Nombre")));
        assertThrows(CatalogFailure.class, () -> catalog.update(actor, id, "\"0\"", null));
    }
}
