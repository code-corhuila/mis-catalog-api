package co.medistock.catalog.core;

import java.util.Locale;

public record NewMedication(String code, String name, String activeIngredient,
        String pharmaceuticalForm, String concentration, String unitOfMeasure) {
    public NewMedication {
        code = text(code, "code", 40);
        if (!code.matches("[A-Za-z0-9-]{1,40}")) throw CatalogFailure.invalid("code");
        code = code.toUpperCase(Locale.ROOT);
        name = text(name, "name", 160);
        activeIngredient = text(activeIngredient, "activeIngredient", 160);
        pharmaceuticalForm = text(pharmaceuticalForm, "pharmaceuticalForm", 80);
        concentration = text(concentration, "concentration", 80);
        unitOfMeasure = text(unitOfMeasure, "unitOfMeasure", 40);
    }

    private static String text(String value, String field, int maximum) {
        if (value == null) throw CatalogFailure.invalid(field);
        String result = value.strip();
        if (result.isBlank() || result.length() > maximum) throw CatalogFailure.invalid(field);
        return result;
    }
}
