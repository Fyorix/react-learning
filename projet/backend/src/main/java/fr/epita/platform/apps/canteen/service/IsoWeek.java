package fr.epita.platform.apps.canteen.service;

import fr.epita.platform.common.ValidationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semaine ISO au format {@code 2026-W47}. */
public final class IsoWeek {

    public static final List<DayOfWeek> WORK_DAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static final Pattern FORMAT = Pattern.compile("^(\\d{4})-W(\\d{2})$");

    private IsoWeek() {
    }

    public static LocalDate monday(String week) {
        Matcher m = FORMAT.matcher(week == null ? "" : week);
        if (!m.matches()) {
            throw ValidationException.field("week", "Format attendu : AAAA-Wss (ex. 2026-W47)");
        }
        int year = Integer.parseInt(m.group(1));
        int number = Integer.parseInt(m.group(2));
        LocalDate reference = LocalDate.of(year, 1, 4);
        if (number < 1 || number > reference.range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).getMaximum()) {
            throw ValidationException.field("week", "Numéro de semaine invalide");
        }
        return reference.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, number)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static String of(LocalDate date) {
        return String.format("%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static DayOfWeek workDay(String value) {
        try {
            DayOfWeek day = DayOfWeek.valueOf(value.toUpperCase());
            if (!WORK_DAYS.contains(day)) {
                throw new IllegalArgumentException();
            }
            return day;
        } catch (IllegalArgumentException e) {
            throw ValidationException.field("dayOfWeek", "Jour attendu : MONDAY à FRIDAY");
        }
    }
}
