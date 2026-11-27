package fr.epita.platform.apps.mood.service;

import fr.epita.platform.apps.mood.dto.MoodDtos.AdminMoodStatsDto;
import fr.epita.platform.apps.mood.dto.MoodDtos.DailyAverage;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodEntryDto;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodEntryRequest;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodStatsDto;
import fr.epita.platform.apps.mood.entity.MoodEntry;
import fr.epita.platform.apps.mood.repository.MoodEntryRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MoodService {

    private final MoodEntryRepository entries;
    private final Clock clock;

    /** Une seule entrée par utilisateur et par jour : une nouvelle saisie met à jour l'entrée existante. */
    @Transactional
    public MoodEntryDto upsert(AuthUser me, LocalDate date, MoodEntryRequest req) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw ValidationException.field("date", "Impossible de saisir une humeur pour une date future");
        }
        MoodEntry entry = entries.findByUserIdAndDate(me.id(), date).orElseGet(() -> {
            MoodEntry e = new MoodEntry();
            e.setUserId(me.id());
            e.setDate(date);
            return e;
        });
        entry.setScore(req.score().shortValue());
        entry.setComment(blankToNull(req.comment()));
        Set<String> tags = new LinkedHashSet<>();
        if (req.tags() != null) {
            req.tags().stream().map(String::trim).filter(t -> !t.isEmpty()).map(String::toLowerCase).forEach(tags::add);
        }
        entry.getTags().clear();
        entry.getTags().addAll(tags);
        return MoodEntryDto.of(entries.saveAndFlush(entry));
    }

    @Transactional(readOnly = true)
    public List<MoodEntryDto> history(AuthUser me, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(30);
        if (start.isAfter(end)) {
            throw ValidationException.field("from", "La date de début doit précéder la date de fin");
        }
        return entries.findByUserIdAndDateBetweenOrderByDateAsc(me.id(), start, end).stream()
                .map(MoodEntryDto::of).toList();
    }

    @Transactional(readOnly = true)
    public MoodEntryDto get(AuthUser me, LocalDate date) {
        return entries.findByUserIdAndDate(me.id(), date).map(MoodEntryDto::of)
                .orElseThrow(() -> ApiException.notFound("Entrée d'humeur"));
    }

    @Transactional
    public void delete(AuthUser me, LocalDate date) {
        MoodEntry entry = entries.findByUserIdAndDate(me.id(), date)
                .orElseThrow(() -> ApiException.notFound("Entrée d'humeur"));
        entries.delete(entry);
    }

    @Transactional(readOnly = true)
    public MoodStatsDto stats(AuthUser me, String period) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = switch (period == null ? "week" : period) {
            case "week" -> today.minusDays(6);
            case "month" -> today.minusMonths(1).plusDays(1);
            case "year" -> today.minusYears(1).plusDays(1);
            default -> throw ValidationException.field("period", "Valeurs possibles : week, month, year");
        };
        List<MoodEntry> inPeriod = entries.findByUserIdAndDateBetweenOrderByDateAsc(me.id(), from, today);
        List<LocalDate> allDates = entries.findByUserIdOrderByDateDesc(me.id()).stream().map(MoodEntry::getDate).toList();
        return new MoodStatsDto(period == null ? "week" : period, from, today, inPeriod.size(), average(inPeriod),
                distribution(inPeriod), currentStreak(allDates, today), longestStreak(allDates));
    }

    @Transactional(readOnly = true)
    public AdminMoodStatsDto adminStats() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(29);
        List<MoodEntry> all = entries.findByDateBetween(from, today);
        long participants = all.stream().map(MoodEntry::getUserId).distinct().count();
        List<DailyAverage> daily = all.stream()
                .collect(Collectors.groupingBy(MoodEntry::getDate))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyAverage(e.getKey(), round(e.getValue().stream().mapToInt(MoodEntry::getScore).average().orElse(0)),
                        e.getValue().size()))
                .toList();
        return new AdminMoodStatsDto(from, today, all.size(), participants, average(all), distribution(all), daily);
    }

    private static Double average(List<MoodEntry> list) {
        return list.isEmpty() ? null : round(list.stream().mapToInt(MoodEntry::getScore).average().orElse(0));
    }

    private static Map<Integer, Long> distribution(List<MoodEntry> list) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int s = 1; s <= 5; s++) {
            final int score = s;
            map.put(s, list.stream().filter(e -> e.getScore() == score).count());
        }
        return map;
    }

    /** Jours consécutifs saisis jusqu'à aujourd'hui (ou hier si aujourd'hui n'est pas encore saisi). */
    static int currentStreak(List<LocalDate> dates, LocalDate today) {
        Set<LocalDate> set = new TreeSet<>(dates);
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    static int longestStreak(List<LocalDate> dates) {
        List<LocalDate> sorted = dates.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : sorted) {
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
