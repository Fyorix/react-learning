package fr.epita.platform.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;

/** Jours fériés français (fixes et mobiles, calculés à partir de Pâques) et jours ouvrés. Aucune API externe. */
public final class CalendarRules {

    private CalendarRules() {
    }

    private static final Set<MonthDay> FIXED = Set.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(5, 8), MonthDay.of(7, 14),
            MonthDay.of(8, 15), MonthDay.of(11, 1), MonthDay.of(11, 11), MonthDay.of(12, 25));

    public static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    public static Set<LocalDate> holidays(int year) {
        Set<LocalDate> days = new HashSet<>();
        FIXED.forEach(md -> days.add(md.atYear(year)));
        LocalDate easter = easter(year);
        days.add(easter.plusDays(1));   // lundi de Pâques
        days.add(easter.plusDays(39));  // Ascension
        days.add(easter.plusDays(50));  // lundi de Pentecôte
        return days;
    }

    public static boolean isHoliday(LocalDate date) {
        return holidays(date.getYear()).contains(date);
    }

    public static boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && !isHoliday(date);
    }
}
