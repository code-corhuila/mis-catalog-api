package co.medistock.catalog.core;

import java.util.List;
import java.util.UUID;

public interface Medications {
    record Creation(Medication medication, boolean created) { }
    record Versioned(Medication medication, long version) { }
    record Meta(int page, int limit, long total, long totalPages) { }
    record Listing(List<Medication> data, Meta meta) { }

    Creation create(UUID actor, String key, NewMedication medication);
    Medication find(UUID id);
    Listing list(int page, int limit);
    Versioned read(UUID id);
    Versioned update(UUID actor, UUID id, long version, NewMedication request);
    Versioned deactivate(UUID actor, UUID id, long version);
}
