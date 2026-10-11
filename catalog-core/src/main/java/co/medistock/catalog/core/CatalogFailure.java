package co.medistock.catalog.core;

public final class CatalogFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    public CatalogFailure(int status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }

    public static CatalogFailure invalid(String field) {
        return new CatalogFailure(400, "VALIDATION_ERROR", "Datos invalidos.", field);
    }
}
