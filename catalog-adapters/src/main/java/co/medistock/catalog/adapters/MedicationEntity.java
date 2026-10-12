package co.medistock.catalog.adapters;

import co.medistock.catalog.core.Medication;
import co.medistock.catalog.core.NewMedication;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "medication", schema = "catalog")
public class MedicationEntity {
    @Id UUID id;
    @Column(nullable = false, length = 40) String code;
    @Column(nullable = false, length = 160) String name;
    @Column(nullable = false, length = 160) String activeIngredient;
    @Column(nullable = false, length = 80) String pharmaceuticalForm;
    @Column(nullable = false, length = 80) String concentration;
    @Column(nullable = false, length = 40) String unitOfMeasure;
    @Column(nullable = false) boolean active;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) UUID createdBy;
    @Column(nullable = false) long version;

    protected MedicationEntity() { }

    MedicationEntity(NewMedication request, UUID actor) {
        id = UUID.randomUUID(); code = request.code(); name = request.name();
        activeIngredient = request.activeIngredient(); pharmaceuticalForm = request.pharmaceuticalForm();
        concentration = request.concentration(); unitOfMeasure = request.unitOfMeasure();
        active = true; createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); createdBy = actor;
    }

    Medication resource() {
        return new Medication(id, code, name, activeIngredient, pharmaceuticalForm,
            concentration, unitOfMeasure, active, createdAt);
    }
}
