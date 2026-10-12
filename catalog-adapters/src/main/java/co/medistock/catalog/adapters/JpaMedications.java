package co.medistock.catalog.adapters;

import co.medistock.catalog.core.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JpaMedications implements Medications {
    private final MedicationRepository medications;
    private final CreationRepository creations;
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public JpaMedications(MedicationRepository medications, CreationRepository creations, JdbcTemplate jdbc) {
        this.medications = medications; this.creations = creations; this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Creation create(UUID actor, String key, NewMedication request) {
        String hash = fingerprint(request);
        // El bloqueo pertenece a la transaccion: serializa la misma intencion, incluso entre replicas.
        jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))",
            Integer.class, actor + ":createMedication:" + key);
        var previous = creations.findByActorAndRequestKey(actor, key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash.equals(hash)) {
                throw new CatalogFailure(409, "IDEMPOTENCY_CONFLICT", "Clave usada con otros datos.", null);
            }
            return new Creation(mapper.readValue(previous.get().responseJson, Medication.class), false);
        }
        MedicationEntity entity = new MedicationEntity(request, actor);
        try {
            medications.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause().getMessage().contains("uq_medication_code")) {
                throw new CatalogFailure(422, "BUSINESS_RULE_VIOLATION", "Codigo ocupado.", "code");
            }
            throw e;
        }
        Medication resource = entity.resource();
        creations.saveAndFlush(new CreationEntity(actor, key, hash, mapper.writeValueAsString(resource), entity.id));
        return new Creation(resource, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Medication find(UUID id) {
        return medications.findById(id).map(MedicationEntity::resource)
            .orElseThrow(() -> new CatalogFailure(404, "NOT_FOUND", "Medicamento inexistente.", null));
    }

    @Override
    @Transactional(readOnly = true)
    public Versioned read(UUID id) {
        var entity = medications.findById(id)
            .orElseThrow(() -> new CatalogFailure(404, "NOT_FOUND", "Medicamento inexistente.", null));
        return new Versioned(entity.resource(), entity.version);
    }

    private MedicationEntity locked(UUID id, long version) {
        var entity = medications.locked(id)
            .orElseThrow(() -> new CatalogFailure(404, "NOT_FOUND", "Medicamento inexistente.", null));
        if (entity.version != version) throw new CatalogFailure(412, "PRECONDITION_FAILED", "El medicamento cambio; consulte de nuevo.", null);
        return entity;
    }

    @Override
    @Transactional
    public Versioned update(UUID actor, UUID id, long version, NewMedication request) {
        var entity = locked(id, version);
        if (!entity.unitOfMeasure.equals(request.unitOfMeasure())) {
            throw new CatalogFailure(422, "BUSINESS_RULE_VIOLATION", "La unidad de medida es inmutable.", "unitOfMeasure");
        }
        var current = new NewMedication(entity.code, entity.name, entity.activeIngredient,
            entity.pharmaceuticalForm, entity.concentration, entity.unitOfMeasure);
        if (current.equals(request)) return new Versioned(entity.resource(), version);
        try {
            jdbc.update("UPDATE catalog.medication SET code=?, name=?, active_ingredient=?, pharmaceutical_form=?, concentration=?, version=version+1, updated_by=?, updated_at=clock_timestamp() WHERE id=?",
                request.code(), request.name(), request.activeIngredient(), request.pharmaceuticalForm(), request.concentration(), actor, id);
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause().getMessage().contains("uq_medication_code")) {
                throw new CatalogFailure(422, "BUSINESS_RULE_VIOLATION", "Codigo ocupado.", "code");
            }
            throw e;
        }
        return new Versioned(new Medication(id, request.code(), request.name(), request.activeIngredient(),
            request.pharmaceuticalForm(), request.concentration(), entity.unitOfMeasure, entity.active, entity.createdAt), version + 1);
    }

    @Override
    @Transactional
    public Versioned deactivate(UUID actor, UUID id, long version) {
        var entity = locked(id, version);
        if (!entity.active) return new Versioned(entity.resource(), version);
        jdbc.update("UPDATE catalog.medication SET active=false, version=version+1, updated_by=?, updated_at=clock_timestamp() WHERE id=?", actor, id);
        var value = entity.resource();
        return new Versioned(new Medication(id, value.code(), value.name(), value.activeIngredient(),
            value.pharmaceuticalForm(), value.concentration(), value.unitOfMeasure(), false, value.createdAt()), version + 1);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Listing list(int page, int limit) {
        if ((long) (page - 1) * limit > Integer.MAX_VALUE) {
            long total = medications.count();
            return new Listing(List.of(), new Meta(page, limit, total, (total + limit - 1) / limit));
        }
        var result = medications.findAll(PageRequest.of(page - 1, limit, Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new Listing(result.getContent().stream().map(MedicationEntity::resource).toList(),
            new Meta(page, limit, result.getTotalElements(), result.getTotalPages()));
    }

    private String fingerprint(NewMedication request) {
        byte[] normalized = mapper.writeValueAsBytes(List.of(request.code(), request.name(),
            request.activeIngredient(), request.pharmaceuticalForm(), request.concentration(), request.unitOfMeasure()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
