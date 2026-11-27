package fr.epita.platform.apps.birthday.service;

import fr.epita.platform.apps.birthday.dto.BirthdayDtos.*;
import fr.epita.platform.apps.birthday.entity.BirthdayProfile;
import fr.epita.platform.apps.birthday.entity.BirthdayTeam;
import fr.epita.platform.apps.birthday.entity.EventType;
import fr.epita.platform.apps.birthday.repository.BirthdayProfileRepository;
import fr.epita.platform.apps.birthday.repository.BirthdayTeamRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dates calculées dans le fuseau Europe/Paris. Un 29 février est fêté le 28 février les années non bissextiles.
 * L'année de naissance n'est jamais renvoyée, sauf à l'intéressé (`/me`).
 */
@Service
@RequiredArgsConstructor
public class BirthdayService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Set<Integer> MILESTONES = Set.of(1, 3, 5);
    static final long MAX_IMPORT = 1024 * 1024;

    private final BirthdayProfileRepository profiles;
    private final BirthdayTeamRepository teams;
    private final UserRepository users;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- calcul des dates ----------

    /** Jour où l'on fête une date (naissance ou arrivée) dans l'année donnée. */
    static LocalDate celebration(LocalDate date, int year) {
        MonthDay md = MonthDay.from(date);
        return md.isValidYear(year) ? md.atYear(year) : LocalDate.of(year, 2, 28);
    }

    static boolean milestone(int years) {
        return MILESTONES.contains(years) || (years >= 10 && years % 5 == 0);
    }

    /** Événements de toutes les fiches entre deux dates incluses (éventuellement sur deux années). */
    private List<EventDto> events(LocalDate from, LocalDate to, UUID teamId) {
        Map<UUID, String> teamNames = teams.findAll().stream().collect(Collectors.toMap(BirthdayTeam::getId, BirthdayTeam::getName));
        List<BirthdayProfile> ps = profiles.findAll().stream().filter(p -> teamId == null || teamId.equals(p.getTeamId())).toList();
        Map<UUID, User> byId = users.findAllById(ps.stream().map(BirthdayProfile::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<EventDto> out = new ArrayList<>();
        for (BirthdayProfile p : ps) {
            User u = byId.get(p.getUserId());
            if (u == null || !u.isEnabled()) {
                continue;
            }
            String team = p.getTeamId() == null ? null : teamNames.get(p.getTeamId());
            for (int year = from.getYear(); year <= to.getYear(); year++) {
                if (p.getBirthDate() != null && p.isShowBirthday()) {
                    LocalDate d = celebration(p.getBirthDate(), year);
                    if (!d.isBefore(from) && !d.isAfter(to)) {
                        out.add(new EventDto(d, u.getId(), u.getDisplayName(), team, EventType.BIRTHDAY, null, false));
                    }
                }
                int years = year - p.getHireDate().getYear();
                if (years >= 1) {
                    LocalDate d = celebration(p.getHireDate(), year);
                    if (!d.isBefore(from) && !d.isAfter(to)) {
                        out.add(new EventDto(d, u.getId(), u.getDisplayName(), team, EventType.ANNIVERSARY, years, milestone(years)));
                    }
                }
            }
        }
        out.sort(Comparator.comparing(EventDto::date).thenComparing(EventDto::displayName));
        return out;
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<EventDto> calendar(YearMonth month, UUID teamId) {
        YearMonth m = month == null ? YearMonth.now(clock.withZone(ZONE)) : month;
        return events(m.atDay(1), m.atEndOfMonth(), teamId);
    }

    @Transactional(readOnly = true)
    public List<EventDto> upcoming(Integer days, UUID teamId) {
        int d = days == null ? 30 : days;
        if (d < 1 || d > 366) {
            throw ValidationException.field("days", "doit être compris entre 1 et 366");
        }
        LocalDate today = today();
        return events(today, today.plusDays(d), teamId);
    }

    @Transactional(readOnly = true)
    public RemindersDto reminders() {
        LocalDate today = today();
        List<EventDto> all = events(today, today.plusDays(1), null);
        return new RemindersDto(today, all.stream().filter(e -> e.date().equals(today)).toList(),
                all.stream().filter(e -> e.date().equals(today.plusDays(1))).toList());
    }

    @Transactional(readOnly = true)
    public MeDto me(AuthUser me) {
        Optional<BirthdayProfile> p = profiles.findById(me.id());
        String name = userLookup.name(me.id());
        return p.map(x -> new MeDto(me.id(), name, x.getBirthDate(), x.getHireDate(), x.getTeamId(),
                        x.getTeamId() == null ? null : teams.findById(x.getTeamId()).map(BirthdayTeam::getName).orElse(null), x.isShowBirthday(), true))
                .orElse(new MeDto(me.id(), name, null, null, null, null, true, false));
    }

    @Transactional
    public MeDto preferences(AuthUser me, boolean showBirthday) {
        BirthdayProfile p = profiles.findById(me.id()).orElseThrow(() -> ApiException.notFound("Fiche (à créer par un administrateur)"));
        p.setShowBirthday(showBirthday);
        profiles.saveAndFlush(p);
        return me(me);
    }

    // ---------- administration ----------

    @Transactional(readOnly = true)
    public List<TeamDto> teams() {
        Map<UUID, Long> counts = profiles.findAll().stream().filter(p -> p.getTeamId() != null)
                .collect(Collectors.groupingBy(BirthdayProfile::getTeamId, Collectors.counting()));
        return teams.findAllByOrderByNameAsc().stream().map(t -> new TeamDto(t.getId(), t.getName(), counts.getOrDefault(t.getId(), 0L))).toList();
    }

    @Transactional
    public TeamDto createTeam(AuthUser me, TeamRequest req) {
        me.requireAdmin();
        if (teams.findByNameIgnoreCase(req.name().trim()).isPresent()) {
            throw ApiException.conflict("TEAM_EXISTS", "Une équipe porte déjà ce nom");
        }
        BirthdayTeam t = new BirthdayTeam();
        t.setName(req.name().trim());
        teams.saveAndFlush(t);
        return new TeamDto(t.getId(), t.getName(), 0);
    }

    @Transactional(readOnly = true)
    public List<PersonDto> people(AuthUser me) {
        me.requireAdmin();
        Map<UUID, String> teamNames = teams.findAll().stream().collect(Collectors.toMap(BirthdayTeam::getId, BirthdayTeam::getName));
        Map<UUID, BirthdayProfile> byUser = profiles.findAll().stream().collect(Collectors.toMap(BirthdayProfile::getUserId, Function.identity()));
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(u -> {
            BirthdayProfile p = byUser.get(u.getId());
            return p == null ? new PersonDto(u.getId(), u.getDisplayName(), null, null, null, null, true)
                    : new PersonDto(u.getId(), u.getDisplayName(), p.getBirthDate() == null ? null : "%02d/%02d".formatted(p.getBirthDate().getDayOfMonth(), p.getBirthDate().getMonthValue()),
                    p.getHireDate(), p.getTeamId(), p.getTeamId() == null ? null : teamNames.get(p.getTeamId()), p.isShowBirthday());
        }).toList();
    }

    @Transactional
    public PersonDto save(AuthUser me, UUID userId, PersonRequest req) {
        me.requireAdmin();
        userLookup.require(userId);
        validateDates(req.birthDate(), req.hireDate());
        if (req.teamId() != null && !teams.existsById(req.teamId())) {
            throw ApiException.notFound("Équipe");
        }
        BirthdayProfile p = profiles.findById(userId).orElseGet(() -> {
            BirthdayProfile n = new BirthdayProfile();
            n.setUserId(userId);
            return n;
        });
        p.setBirthDate(req.birthDate());
        p.setHireDate(req.hireDate());
        p.setTeamId(req.teamId());
        profiles.saveAndFlush(p);
        return people(me).stream().filter(x -> x.userId().equals(userId)).findFirst().orElseThrow();
    }

    private void validateDates(LocalDate birth, LocalDate hire) {
        var errors = new ValidationException.Collector();
        errors.check(birth == null || birth.isBefore(today()), "birthDate", "doit être dans le passé");
        errors.check(!hire.isAfter(today()), "hireDate", "ne doit pas être dans le futur");
        errors.check(birth == null || birth.isBefore(hire), "hireDate", "doit être postérieure à la date de naissance");
        errors.throwIfAny();
    }

    /** Import `email;birthDate;hireDate;team` : chaque ligne est traitée indépendamment, avec un rapport ligne par ligne. */
    @Transactional
    public ImportReport importCsv(AuthUser me, MultipartFile file) {
        me.requireAdmin();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_FILE", "Fichier CSV manquant");
        }
        if (file.getSize() > MAX_IMPORT) {
            throw ApiException.badRequest("INVALID_FILE", "Fichier trop volumineux (1 Mo maximum)");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_FILE", "Fichier illisible");
        }
        List<ImportLine> report = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && line.toLowerCase().startsWith("email"))) {
                continue;
            }
            report.add(importLine(i + 1, line));
        }
        profiles.flush();
        int ok = (int) report.stream().filter(ImportLine::ok).count();
        return new ImportReport(ok, report.size() - ok, report);
    }

    private ImportLine importLine(int number, String line) {
        String[] f = line.split(";", -1);
        String email = f.length > 0 ? f[0].trim().toLowerCase() : "";
        if (f.length != 4) {
            return new ImportLine(number, email, false, "4 colonnes attendues : email;birthDate;hireDate;team");
        }
        Optional<User> user = users.findByEmail(email);
        if (user.isEmpty()) {
            return new ImportLine(number, email, false, "utilisateur inconnu");
        }
        LocalDate birth;
        LocalDate hire;
        try {
            birth = f[1].isBlank() ? null : LocalDate.parse(f[1].trim());
            hire = LocalDate.parse(f[2].trim());
        } catch (DateTimeParseException e) {
            return new ImportLine(number, email, false, "date invalide (format AAAA-MM-JJ)");
        }
        try {
            validateDates(birth, hire);
        } catch (ValidationException e) {
            return new ImportLine(number, email, false, String.join(", ", e.getFields().values()));
        }
        UUID teamId = null;
        if (!f[3].isBlank()) {
            String name = f[3].trim();
            teamId = teams.findByNameIgnoreCase(name).orElseGet(() -> {
                BirthdayTeam t = new BirthdayTeam();
                t.setName(name);
                return teams.saveAndFlush(t);
            }).getId();
        }
        BirthdayProfile p = profiles.findById(user.get().getId()).orElseGet(BirthdayProfile::new);
        p.setUserId(user.get().getId());
        p.setBirthDate(birth);
        p.setHireDate(hire);
        p.setTeamId(teamId);
        profiles.save(p);
        return new ImportLine(number, email, true, "importé");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
