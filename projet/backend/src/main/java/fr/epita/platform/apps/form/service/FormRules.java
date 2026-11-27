package fr.epita.platform.apps.form.service;

import com.fasterxml.jackson.databind.JsonNode;
import fr.epita.platform.apps.form.dto.FormDtos.Condition;
import fr.epita.platform.apps.form.dto.FormDtos.FieldDef;
import fr.epita.platform.apps.form.entity.FieldType;
import fr.epita.platform.common.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Règles des champs, partagées par l'enregistrement de la structure et la validation des réponses
 * (le front en a une copie identique dans son registre de types).
 */
final class FormRules {

    private static final Pattern FIELD_ID = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,39}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private FormRules() {
    }

    // ---------- structure ----------

    /** Erreurs avec le chemin du champ fautif, ex. `fields[2].config.max`. */
    static void validateStructure(List<FieldDef> fields) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            String p = "fields[" + i + "]";
            if (f.id() == null || !FIELD_ID.matcher(f.id()).matches()) {
                errors.put(p + ".id", "identifiant invalide (lettre puis lettres, chiffres ou _)");
            } else if (seen.putIfAbsent(f.id(), i) != null) {
                errors.put(p + ".id", "identifiant dupliqué : « " + f.id() + " »");
            }
            if (f.type() == null) {
                errors.put(p + ".type", "type obligatoire");
                continue;
            }
            if (f.label() == null || f.label().isBlank()) {
                errors.put(p + ".label", "libellé obligatoire");
            } else if (f.label().length() > 200) {
                errors.put(p + ".label", "200 caractères maximum");
            }
            Map<String, Object> c = f.config() == null ? Map.of() : f.config();
            switch (f.type()) {
                case TEXT -> {
                    minMax(errors, p, c, "minLength", "maxLength", true);
                    Object pattern = c.get("pattern");
                    if (pattern != null) {
                        try {
                            Pattern.compile(String.valueOf(pattern));
                        } catch (PatternSyntaxException e) {
                            errors.put(p + ".config.pattern", "expression régulière invalide");
                        }
                    }
                }
                case LONG_TEXT -> {
                    Double max = num(c, "maxLength");
                    if (c.containsKey("maxLength") && (max == null || max < 1)) {
                        errors.put(p + ".config.maxLength", "doit être un entier ≥ 1");
                    }
                }
                case NUMBER -> {
                    minMax(errors, p, c, "min", "max", false);
                    Double step = num(c, "step");
                    if (c.containsKey("step") && (step == null || step <= 0)) {
                        errors.put(p + ".config.step", "doit être > 0");
                    }
                }
                case DATE -> {
                    LocalDate min = date(errors, p + ".config.min", c.get("min"));
                    LocalDate max = date(errors, p + ".config.max", c.get("max"));
                    if (min != null && max != null && min.isAfter(max)) {
                        errors.put(p + ".config.max", "doit être postérieure à min");
                    }
                }
                case SELECT, RADIO -> options(errors, p, c);
                case CHECKBOXES -> {
                    int n = options(errors, p, c);
                    minMax(errors, p, c, "minSelected", "maxSelected", true);
                    Double maxSel = num(c, "maxSelected");
                    if (maxSel != null && n > 0 && maxSel > n) {
                        errors.put(p + ".config.maxSelected", "ne peut pas dépasser le nombre d'options (" + n + ")");
                    }
                }
                case RATING -> {
                    Double max = num(c, "max");
                    if (c.containsKey("max") && (max == null || max < 2 || max > 10 || max % 1 != 0)) {
                        errors.put(p + ".config.max", "doit être un entier entre 2 et 10");
                    }
                }
                case EMAIL -> {
                }
            }
            Condition cond = f.condition();
            if (cond != null) {
                Integer target = seen.get(cond.fieldId());
                if (target == null || target >= i) {
                    errors.put(p + ".condition.fieldId", "doit désigner un champ placé avant celui-ci");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void minMax(Map<String, String> errors, String p, Map<String, Object> c, String minKey, String maxKey, boolean nonNegative) {
        Double min = num(c, minKey);
        Double max = num(c, maxKey);
        if (c.containsKey(minKey) && min == null) {
            errors.put(p + ".config." + minKey, "doit être un nombre");
        }
        if (c.containsKey(maxKey) && max == null) {
            errors.put(p + ".config." + maxKey, "doit être un nombre");
        }
        if (nonNegative && min != null && min < 0) {
            errors.put(p + ".config." + minKey, "doit être ≥ 0");
        }
        if (min != null && max != null && min > max) {
            errors.put(p + ".config." + maxKey, "doit être ≥ " + minKey);
        }
    }

    private static int options(Map<String, String> errors, String p, Map<String, Object> c) {
        if (!(c.get("options") instanceof List<?> list) || list.isEmpty()) {
            errors.put(p + ".config.options", "au moins une option");
            return 0;
        }
        Set<String> values = new HashSet<>();
        for (int j = 0; j < list.size(); j++) {
            Object o = list.get(j);
            String value = o instanceof Map<?, ?> m && m.get("value") != null ? String.valueOf(m.get("value")) : null;
            String label = o instanceof Map<?, ?> m && m.get("label") != null ? String.valueOf(m.get("label")) : null;
            if (value == null || value.isBlank()) {
                errors.put(p + ".config.options[" + j + "].value", "valeur obligatoire");
            } else if (!values.add(value)) {
                errors.put(p + ".config.options[" + j + "].value", "valeur dupliquée : « " + value + " »");
            }
            if (label == null || label.isBlank()) {
                errors.put(p + ".config.options[" + j + "].label", "libellé obligatoire");
            }
        }
        return list.size();
    }

    private static LocalDate date(Map<String, String> errors, String path, Object v) {
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (DateTimeParseException e) {
            errors.put(path, "date invalide (AAAA-MM-JJ)");
            return null;
        }
    }

    static Double num(Map<String, Object> c, String key) {
        Object v = c.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static List<String> optionValues(Map<String, Object> c) {
        List<String> out = new ArrayList<>();
        if (c.get("options") instanceof List<?> list) {
            list.forEach(o -> {
                if (o instanceof Map<?, ?> m && m.get("value") != null) {
                    out.add(String.valueOf(m.get("value")));
                }
            });
        }
        return out;
    }

    // ---------- réponses ----------

    record Checked(Map<String, Object> values, Map<String, String> errors, List<String> hidden) {
    }

    /** Valide les réponses selon la configuration ; un champ masqué par sa condition n'est ni validé ni conservé. */
    static Checked validateAnswers(List<FieldDef> fields, Map<String, JsonNode> answers) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> hidden = new ArrayList<>();
        for (FieldDef f : fields) {
            String key = "answers." + f.id();
            if (!visible(f, values, hidden)) {
                hidden.add(f.id());
                continue;
            }
            JsonNode a = answers.get(f.id());
            boolean empty = a == null || a.isNull() || (a.isTextual() && a.asText().isBlank()) || (a.isArray() && a.isEmpty());
            if (empty) {
                if (f.required()) {
                    errors.put(key, "réponse obligatoire");
                }
                continue;
            }
            Map<String, Object> c = f.config() == null ? Map.of() : f.config();
            String error = null;
            Object value = null;
            switch (f.type()) {
                case TEXT, LONG_TEXT, EMAIL -> {
                    if (!a.isTextual()) {
                        error = "texte attendu";
                        break;
                    }
                    String s = a.asText().trim();
                    Double min = num(c, "minLength");
                    Double max = num(c, "maxLength");
                    if (min != null && s.length() < min) {
                        error = "au moins " + min.intValue() + " caractères";
                    } else if (max != null && s.length() > max) {
                        error = "au plus " + max.intValue() + " caractères";
                    } else if (f.type() == FieldType.TEXT && c.get("pattern") != null && !Pattern.compile(String.valueOf(c.get("pattern"))).matcher(s).matches()) {
                        error = "format invalide";
                    } else if (f.type() == FieldType.EMAIL && !EMAIL.matcher(s).matches()) {
                        error = "adresse e-mail invalide";
                    } else if (s.length() > 10000) {
                        error = "10 000 caractères maximum";
                    }
                    value = s;
                }
                case NUMBER -> {
                    if (!a.isNumber()) {
                        error = "nombre attendu";
                        break;
                    }
                    double d = a.asDouble();
                    Double min = num(c, "min");
                    Double max = num(c, "max");
                    Double step = num(c, "step");
                    if (min != null && d < min) {
                        error = "doit être ≥ " + fmt(min);
                    } else if (max != null && d > max) {
                        error = "doit être ≤ " + fmt(max);
                    } else if (step != null && Math.abs(((d - (min == null ? 0 : min)) / step) - Math.round((d - (min == null ? 0 : min)) / step)) > 1e-9) {
                        error = "doit être un multiple de " + fmt(step);
                    }
                    value = a.numberValue();
                }
                case DATE -> {
                    try {
                        LocalDate d = LocalDate.parse(a.asText());
                        if (c.get("min") != null && d.isBefore(LocalDate.parse(String.valueOf(c.get("min"))))) {
                            error = "au plus tôt le " + c.get("min");
                        } else if (c.get("max") != null && d.isAfter(LocalDate.parse(String.valueOf(c.get("max"))))) {
                            error = "au plus tard le " + c.get("max");
                        }
                        value = d.toString();
                    } catch (DateTimeParseException e) {
                        error = "date invalide (AAAA-MM-JJ)";
                    }
                }
                case SELECT, RADIO -> {
                    if (!a.isTextual() || !optionValues(c).contains(a.asText())) {
                        error = "choix inconnu";
                    }
                    value = a.asText();
                }
                case CHECKBOXES -> {
                    if (!a.isArray()) {
                        error = "liste de choix attendue";
                        break;
                    }
                    List<String> picked = new ArrayList<>();
                    a.forEach(x -> picked.add(x.asText()));
                    List<String> allowed = optionValues(c);
                    Double min = num(c, "minSelected");
                    Double max = num(c, "maxSelected");
                    if (!allowed.containsAll(picked) || new HashSet<>(picked).size() != picked.size()) {
                        error = "choix inconnu ou en double";
                    } else if (min != null && picked.size() < min) {
                        error = "au moins " + min.intValue() + " choix";
                    } else if (max != null && picked.size() > max) {
                        error = "au plus " + max.intValue() + " choix";
                    }
                    value = picked;
                }
                case RATING -> {
                    int max = num(c, "max") == null ? 5 : num(c, "max").intValue();
                    if (!a.isInt() || a.asInt() < 1 || a.asInt() > max) {
                        error = "note entière de 1 à " + max;
                    }
                    value = a.asInt();
                }
            }
            if (error != null) {
                errors.put(key, error);
            } else {
                values.put(f.id(), value);
            }
        }
        return new Checked(values, errors, hidden);
    }

    /** Condition évaluée sur les valeurs déjà validées (le champ cible est placé avant). */
    private static boolean visible(FieldDef f, Map<String, Object> values, List<String> hidden) {
        Condition c = f.condition();
        if (c == null) {
            return true;
        }
        if (hidden.contains(c.fieldId())) {
            return false;
        }
        Object v = values.get(c.fieldId());
        boolean matches = switch (c.operator()) {
            case EQUALS -> v instanceof List<?> l ? l.contains(c.value()) : v != null && fmtValue(v).equals(c.value());
            case NOT_EQUALS -> v instanceof List<?> l ? !l.contains(c.value()) : v == null || !fmtValue(v).equals(c.value());
            case CONTAINS -> v instanceof List<?> l ? l.contains(c.value()) : v != null && fmtValue(v).toLowerCase().contains(c.value().toLowerCase());
        };
        return matches;
    }

    private static String fmtValue(Object v) {
        return v instanceof Number n ? fmt(n.doubleValue()) : String.valueOf(v);
    }

    private static String fmt(double d) {
        return d % 1 == 0 ? String.valueOf((long) d) : String.valueOf(d);
    }
}
