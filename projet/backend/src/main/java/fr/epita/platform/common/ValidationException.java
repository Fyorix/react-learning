package fr.epita.platform.common;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erreur de validation métier (règles impossibles à exprimer en annotations) : 400 avec le détail par champ.
 */
@Getter
public class ValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super("Données invalides");
        this.fields = fields;
    }

    public static ValidationException field(String field, String message) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(field, message);
        return new ValidationException(map);
    }

    /** Accumule les erreurs puis lève l'exception si au moins une est présente. */
    public static class Collector {
        private final Map<String, String> fields = new LinkedHashMap<>();

        public Collector check(boolean ok, String field, String message) {
            if (!ok) {
                fields.putIfAbsent(field, message);
            }
            return this;
        }

        public void throwIfAny() {
            if (!fields.isEmpty()) {
                throw new ValidationException(fields);
            }
        }
    }
}
