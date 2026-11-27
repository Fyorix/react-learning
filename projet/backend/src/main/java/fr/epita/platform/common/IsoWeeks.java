package fr.epita.platform.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semaines ISO au format AAAA-Www (ex. 2026-W48) : conversion vers le lundi et inversement. */
public final class IsoWeeks {

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-W(\\d{2})");

    private IsoWeeks() {
    }

    public static LocalDate monday(String week) {
        Matcher m = week == null ? null : FORMAT.matcher(week);
        if (m == null || !m.matches()) {
            throw ValidationException.field("week", "doit respecter le format AAAA-Www (ex. 2026-W48)");
        }
        int year = Integer.parseInt(m.group(1));
        int number = Integer.parseInt(m.group(2));
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        if (number < 1 || number > jan4.range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).getMaximum()) {
            throw ValidationException.field("week", "numéro de semaine invalide");
        }
        return jan4.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, number).with(DayOfWeek.MONDAY);
    }

    public static String of(LocalDate date) {
        return "%d-W%02d".formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static List<LocalDate> days(String week) {
        LocalDate monday = monday(week);
        return monday.datesUntil(monday.plusDays(7)).toList();
    }
}
