package fr.epita.platform.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Numérotation continue par année (devis, factures, avoirs) : table {@code <nom>_sequences (year, last_value)}.
 * L'incrément est atomique et fait partie de la transaction appelante : un rollback ne laisse pas de trou.
 */
@Component
@RequiredArgsConstructor
public class Sequences {

    private static final Pattern TABLE = Pattern.compile("[a-z_]+_sequences");

    private final JdbcTemplate jdbc;

    public String next(String table, String prefix, int year) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException(table);
        }
        Integer value = jdbc.queryForObject("insert into " + table + " (year, last_value) values (?, 1) "
                + "on conflict (year) do update set last_value = " + table + ".last_value + 1 returning last_value",
                Integer.class, year);
        return "%s-%d-%04d".formatted(prefix, year, value);
    }
}
