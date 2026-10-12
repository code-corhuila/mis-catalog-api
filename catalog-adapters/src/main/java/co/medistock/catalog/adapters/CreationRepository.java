package co.medistock.catalog.adapters;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreationRepository extends JpaRepository<CreationEntity, UUID> {
    Optional<CreationEntity> findByActorAndRequestKey(UUID actor, String requestKey);
}
