package co.medistock.catalog.adapters;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "creation_request", schema = "catalog")
public class CreationEntity {
    @Id UUID id;
    @Column(nullable = false) UUID actor;
    @Column(nullable = false, length = 128) String requestKey;
    @Column(nullable = false, length = 64) String requestHash;
    @Column(nullable = false, columnDefinition = "text") String responseJson;
    @Column(nullable = false) UUID medicationId;

    protected CreationEntity() { }

    CreationEntity(UUID actor, String key, String hash, String response, UUID medicationId) {
        id = UUID.randomUUID(); this.actor = actor; requestKey = key;
        requestHash = hash; responseJson = response; this.medicationId = medicationId;
    }
}
