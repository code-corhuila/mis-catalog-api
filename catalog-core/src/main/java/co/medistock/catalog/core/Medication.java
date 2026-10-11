package co.medistock.catalog.core;

import java.time.Instant;
import java.util.UUID;

public record Medication(UUID id, String code, String name, String activeIngredient,
        String pharmaceuticalForm, String concentration, String unitOfMeasure,
        boolean active, Instant createdAt) { }
