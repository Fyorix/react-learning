package fr.epita.platform.apps.tournament.service;

import fr.epita.platform.apps.tournament.dto.TournamentDtos.*;
import fr.epita.platform.apps.tournament.entity.*;
import fr.epita.platform.apps.tournament.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentService {

    static final int K = 32;
    static final int START_ELO = 1000;
    static final int MIN_ENTRIES = 4;
    static final int WINNING_SCORE = 10;

    private final TournamentRepository tournaments;
    private final EntryRepository entries;
    private final MatchRepository matches;
    private final RatingRepository ratings;
    private final TournamentRandom random;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- tournois ----------

    @Transactional(readOnly = true)
    public List<TournamentSummary> list(AuthUser me, TournamentStatus status) {
        Set<UUID> mine = entries.ofPlayer(me.id()).stream().map(Entry::getTournamentId).collect(Collectors.toSet());
        return tournaments.findAllByOrderByRegistrationDeadlineDesc().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(t -> new TournamentSummary(t.getId(), t.getName(), t.getFormat(), t.getMode(), t.getStatus(), t.getRegistrationDeadline(),
                        userLookup.name(t.getOrganizerId()), entries.countByTournamentId(t.getId()), mine.contains(t.getId()))).toList();
    }

    @Transactional
    public TournamentDto create(AuthUser me, TournamentRequest req) {
        if (!req.registrationDeadline().isAfter(clock.instant())) {
            throw ValidationException.field("registrationDeadline", "doit être dans le futur");
        }
        Tournament t = new Tournament();
        t.setName(req.name().trim());
        t.setFormat(req.format());
        t.setMode(req.mode());
        t.setRegistrationDeadline(req.registrationDeadline());
        t.setOrganizerId(me.id());
        t.setStatus(TournamentStatus.REGISTRATION);
        return get(me, tournaments.saveAndFlush(t).getId());
    }

    @Transactional(readOnly = true)
    public TournamentDto get(AuthUser me, UUID id) {
        Tournament t = find(id);
        List<Entry> es = entries.findByTournamentIdOrderByCreatedAtAsc(id);
        Map<UUID, EntryDto> dtos = entryDtos(es);
        List<Match> ms = matches.findByTournamentIdOrderByRoundAscPositionAsc(id);
        List<MatchDto> matchDtos = ms.stream().map(m -> toDto(me, m, dtos, null)).toList();
        List<StandingDto> standings = t.getFormat() == Format.ROUND_ROBIN && !ms.isEmpty() ? standings(es, ms, dtos) : List.of();
        EntryDto champion = null;
        if (t.getStatus() == TournamentStatus.FINISHED) {
            champion = t.getFormat() == Format.SINGLE_ELIMINATION
                    ? ms.stream().max(Comparator.comparingInt(Match::getRound)).map(m -> dtos.get(m.getWinnerId())).orElse(null)
                    : standings.isEmpty() ? null : dtos.get(standings.get(0).entryId());
        }
        int rounds = ms.stream().mapToInt(Match::getRound).max().orElse(0);
        boolean registered = es.stream().anyMatch(e -> e.has(me.id()));
        List<EntryDto> sortedEntries = es.stream().map(e -> dtos.get(e.getId()))
                .sorted(Comparator.comparing((EntryDto e) -> e.seed() == null ? Integer.MAX_VALUE : e.seed()).thenComparing(EntryDto::elo, Comparator.reverseOrder())).toList();
        return new TournamentDto(t.getId(), t.getName(), t.getFormat(), t.getMode(), t.getStatus(), t.getRegistrationDeadline(),
                t.getOrganizerId(), userLookup.name(t.getOrganizerId()), t.getOrganizerId().equals(me.id()), registered, rounds,
                sortedEntries, matchDtos, standings, champion);
    }

    // ---------- inscriptions ----------

    @Transactional
    public TournamentDto register(AuthUser me, UUID id, UUID partnerId) {
        Tournament t = tournaments.lock(id).orElseThrow(() -> ApiException.notFound("Tournoi"));
        if (t.getStatus() != TournamentStatus.REGISTRATION || !clock.instant().isBefore(t.getRegistrationDeadline())) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "Les inscriptions sont closes");
        }
        if (t.getMode() == Mode.DOUBLES && partnerId == null) {
            throw ValidationException.field("partnerId", "obligatoire en double");
        }
        if (t.getMode() == Mode.SINGLES && partnerId != null) {
            throw ValidationException.field("partnerId", "interdit en simple");
        }
        if (me.id().equals(partnerId)) {
            throw ValidationException.field("partnerId", "vous ne pouvez pas être votre propre coéquipier");
        }
        List<Entry> es = entries.findByTournamentIdOrderByCreatedAtAsc(id);
        if (es.stream().anyMatch(e -> e.has(me.id()))) {
            throw ApiException.conflict("ALREADY_REGISTERED", "Vous êtes déjà inscrit à ce tournoi");
        }
        if (partnerId != null) {
            userLookup.require(partnerId);
            if (es.stream().anyMatch(e -> e.has(partnerId))) {
                throw ApiException.conflict("PARTNER_ALREADY_REGISTERED", "Votre coéquipier est déjà inscrit");
            }
        }
        Entry e = new Entry();
        e.setTournamentId(id);
        e.setPlayer1Id(me.id());
        e.setPlayer2Id(partnerId);
        entries.saveAndFlush(e);
        return get(me, id);
    }

    @Transactional
    public TournamentDto unregister(AuthUser me, UUID id) {
        Tournament t = tournaments.lock(id).orElseThrow(() -> ApiException.notFound("Tournoi"));
        if (t.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "Le tableau est déjà généré");
        }
        Entry e = entries.findByTournamentIdOrderByCreatedAtAsc(id).stream().filter(x -> x.has(me.id())).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        entries.delete(e);
        entries.flush();
        return get(me, id);
    }

    // ---------- génération du tableau ----------

    @Transactional
    public TournamentDto start(AuthUser me, UUID id) {
        Tournament t = tournaments.lock(id).orElseThrow(() -> ApiException.notFound("Tournoi"));
        if (!t.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Action réservée à l'organisateur");
        }
        if (t.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le tableau est déjà généré");
        }
        List<Entry> es = new ArrayList<>(entries.findByTournamentIdOrderByCreatedAtAsc(id));
        if (es.size() < MIN_ENTRIES) {
            throw ApiException.conflict("NOT_ENOUGH_PLAYERS", MIN_ENTRIES + " participants minimum (" + es.size() + " inscrits)");
        }
        // aléa d'abord, puis tri stable par Elo : les Elo égaux sont départagés au hasard
        random.shuffle(es);
        Map<UUID, Integer> elo = eloOf(es);
        es.sort(Comparator.comparing((Entry e) -> elo.get(e.getId())).reversed());
        for (int i = 0; i < es.size(); i++) {
            es.get(i).setSeed(i + 1);
        }
        entries.saveAll(es);
        t.setStatus(TournamentStatus.IN_PROGRESS);
        if (t.getFormat() == Format.SINGLE_ELIMINATION) {
            bracket(t, es);
        } else {
            roundRobin(t, es);
        }
        tournaments.saveAndFlush(t);
        return get(me, id);
    }

    /** Ordre de tableau standard : 1 contre N, 2 contre N-1… ; les meilleures têtes de série sont exemptées. */
    static List<Integer> seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int m = order.size() * 2 + 1;
            List<Integer> next = new ArrayList<>();
            for (int s : order) {
                next.add(s);
                next.add(m - s);
            }
            order = next;
        }
        return order;
    }

    private void bracket(Tournament t, List<Entry> seeded) {
        int size = Integer.highestOneBit(seeded.size() - 1) << 1;
        List<Integer> order = seedOrder(size);
        int rounds = Integer.numberOfTrailingZeros(size);
        for (int r = 1; r <= rounds; r++) {
            for (int p = 0; p < size >> r; p++) {
                Match m = new Match();
                m.setTournamentId(t.getId());
                m.setRound(r);
                m.setPosition(p);
                m.setStatus(MatchStatus.WAITING);
                matches.save(m);
            }
        }
        matches.flush();
        for (int p = 0; p < size / 2; p++) {
            Match m = matches.findByTournamentIdAndRoundAndPosition(t.getId(), 1, p).orElseThrow();
            int sa = order.get(2 * p);
            int sb = order.get(2 * p + 1);
            m.setEntryAId(sa <= seeded.size() ? seeded.get(sa - 1).getId() : null);
            m.setEntryBId(sb <= seeded.size() ? seeded.get(sb - 1).getId() : null);
            if (m.getEntryAId() == null || m.getEntryBId() == null) {
                m.setStatus(MatchStatus.BYE);
                m.setWinnerId(m.getEntryAId() != null ? m.getEntryAId() : m.getEntryBId());
                matches.save(m);
                advance(m);
            } else {
                m.setStatus(MatchStatus.SCHEDULED);
                matches.save(m);
            }
        }
    }

    private void roundRobin(Tournament t, List<Entry> es) {
        List<Entry[]> pairs = new ArrayList<>();
        for (int i = 0; i < es.size(); i++) {
            for (int j = i + 1; j < es.size(); j++) {
                pairs.add(new Entry[]{es.get(i), es.get(j)});
            }
        }
        random.shuffle(pairs);
        for (int p = 0; p < pairs.size(); p++) {
            Match m = new Match();
            m.setTournamentId(t.getId());
            m.setRound(1);
            m.setPosition(p);
            m.setEntryAId(pairs.get(p)[0].getId());
            m.setEntryBId(pairs.get(p)[1].getId());
            m.setStatus(MatchStatus.SCHEDULED);
            matches.save(m);
        }
    }

    /** Le vainqueur passe au tour suivant (position / 2, camp A si position paire). */
    private void advance(Match m) {
        Optional<Match> next = matches.findByTournamentIdAndRoundAndPosition(m.getTournamentId(), m.getRound() + 1, m.getPosition() / 2);
        if (next.isEmpty()) {
            Tournament t = tournaments.findById(m.getTournamentId()).orElseThrow();
            t.setStatus(TournamentStatus.FINISHED);
            tournaments.save(t);
            return;
        }
        Match n = next.get();
        if (m.getPosition() % 2 == 0) {
            n.setEntryAId(m.getWinnerId());
        } else {
            n.setEntryBId(m.getWinnerId());
        }
        if (n.getEntryAId() != null && n.getEntryBId() != null) {
            n.setStatus(MatchStatus.SCHEDULED);
        }
        matches.save(n);
    }

    // ---------- scores ----------

    @Transactional
    public MatchDto report(AuthUser me, UUID matchId, ScoreRequest req) {
        Match m = matches.lock(matchId).orElseThrow(() -> ApiException.notFound("Match"));
        sideOf(me, m);
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", m.getStatus() == MatchStatus.REPORTED
                    ? "Un score est déjà saisi : l'adversaire doit le confirmer ou le contester" : "Ce match ne peut pas recevoir de score");
        }
        int a = req.scoreA();
        int b = req.scoreB();
        if (!((a == WINNING_SCORE) ^ (b == WINNING_SCORE))) {
            throw ValidationException.field("scoreA", "exactement un camp doit atteindre " + WINNING_SCORE + " points");
        }
        m.setScoreA(a);
        m.setScoreB(b);
        m.setReportedBy(me.id());
        m.setStatus(MatchStatus.REPORTED);
        return toDto(me, matches.saveAndFlush(m));
    }

    /** Confirmation par un joueur du camp adverse à celui qui a saisi ; met à jour l'Elo et fait avancer le vainqueur. */
    @Transactional
    public MatchDto confirm(AuthUser me, UUID matchId) {
        Match m = reportedByOpponent(me, matchId);
        m.setWinnerId(m.getScoreA() > m.getScoreB() ? m.getEntryAId() : m.getEntryBId());
        m.setStatus(MatchStatus.CONFIRMED);
        m.setConfirmedAt(clock.instant());
        matches.save(m);
        updateElo(m);
        Tournament t = tournaments.findById(m.getTournamentId()).orElseThrow();
        if (t.getFormat() == Format.SINGLE_ELIMINATION) {
            advance(m);
        } else if (matches.findByTournamentIdOrderByRoundAscPositionAsc(t.getId()).stream().allMatch(x -> x.getStatus() == MatchStatus.CONFIRMED)) {
            t.setStatus(TournamentStatus.FINISHED);
            tournaments.save(t);
        }
        matches.flush();
        return toDto(me, m);
    }

    @Transactional
    public MatchDto dispute(AuthUser me, UUID matchId) {
        Match m = reportedByOpponent(me, matchId);
        m.setScoreA(null);
        m.setScoreB(null);
        m.setReportedBy(null);
        m.setStatus(MatchStatus.SCHEDULED);
        return toDto(me, matches.saveAndFlush(m));
    }

    private Match reportedByOpponent(AuthUser me, UUID matchId) {
        Match m = matches.lock(matchId).orElseThrow(() -> ApiException.notFound("Match"));
        char mySide = sideOf(me, m);
        if (m.getStatus() != MatchStatus.REPORTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Aucun score en attente de confirmation");
        }
        char reporterSide = entries.findById(m.getEntryAId()).orElseThrow().has(m.getReportedBy()) ? 'A' : 'B';
        if (mySide == reporterSide) {
            throw ApiException.conflict("SCORE_NOT_CONFIRMABLE", "Le score doit être confirmé par un joueur du camp adverse");
        }
        return m;
    }

    private char sideOf(AuthUser me, Match m) {
        if (m.getEntryAId() != null && entries.findById(m.getEntryAId()).orElseThrow().has(me.id())) {
            return 'A';
        }
        if (m.getEntryBId() != null && entries.findById(m.getEntryBId()).orElseThrow().has(me.id())) {
            return 'B';
        }
        throw ApiException.forbidden("Vous ne jouez pas ce match");
    }

    // ---------- Elo ----------

    private void updateElo(Match m) {
        Entry a = entries.findById(m.getEntryAId()).orElseThrow();
        Entry b = entries.findById(m.getEntryBId()).orElseThrow();
        List<Rating> ra = ratingsOf(a);
        List<Rating> rb = ratingsOf(b);
        double eloA = ra.stream().mapToInt(Rating::getElo).average().orElse(START_ELO);
        double eloB = rb.stream().mapToInt(Rating::getElo).average().orElse(START_ELO);
        boolean aWins = m.getWinnerId().equals(a.getId());
        double expectedA = 1 / (1 + Math.pow(10, (eloB - eloA) / 400));
        int delta = (int) Math.round(K * ((aWins ? 1 : 0) - expectedA));
        ra.forEach(r -> apply(r, delta, aWins));
        rb.forEach(r -> apply(r, -delta, !aWins));
    }

    private void apply(Rating r, int delta, boolean win) {
        r.setElo(r.getElo() + delta);
        r.setPlayed(r.getPlayed() + 1);
        r.setWins(r.getWins() + (win ? 1 : 0));
        r.setUpdatedAt(clock.instant());
        ratings.save(r);
    }

    private List<Rating> ratingsOf(Entry e) {
        List<Rating> list = new ArrayList<>();
        list.add(rating(e.getPlayer1Id()));
        if (e.getPlayer2Id() != null) {
            list.add(rating(e.getPlayer2Id()));
        }
        return list;
    }

    private Rating rating(UUID userId) {
        return ratings.findById(userId).orElseGet(() -> {
            Rating r = new Rating();
            r.setUserId(userId);
            r.setElo(START_ELO);
            r.setUpdatedAt(clock.instant());
            return r;
        });
    }

    private int eloOfUser(UUID userId) {
        return ratings.findById(userId).map(Rating::getElo).orElse(START_ELO);
    }

    private Map<UUID, Integer> eloOf(Collection<Entry> es) {
        return es.stream().collect(Collectors.toMap(Entry::getId, e -> e.getPlayer2Id() == null ? eloOfUser(e.getPlayer1Id())
                : Math.round((eloOfUser(e.getPlayer1Id()) + eloOfUser(e.getPlayer2Id())) / 2f)));
    }

    // ---------- classement et profils ----------

    @Transactional(readOnly = true)
    public List<RankingRow> ranking() {
        List<Rating> rs = ratings.findByPlayedGreaterThanOrderByEloDescWinsDesc(0);
        Map<UUID, String> names = userLookup.namesOf(rs, Rating::getUserId);
        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < rs.size(); i++) {
            Rating r = rs.get(i);
            rows.add(new RankingRow(i + 1, r.getUserId(), names.get(r.getUserId()), r.getElo(), r.getPlayed(), r.getWins()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public PlayerDto player(AuthUser me, UUID userId) {
        userLookup.require(userId);
        Rating r = rating(userId);
        List<Entry> es = entries.ofPlayer(userId);
        List<MatchDto> history = es.isEmpty() ? List.of() : matches.ofEntries(es.stream().map(Entry::getId).toList()).stream()
                .filter(m -> m.getStatus() == MatchStatus.CONFIRMED)
                .sorted(Comparator.comparing(Match::getConfirmedAt).reversed())
                .map(m -> toDto(me, m)).toList();
        return new PlayerDto(userId, userLookup.name(userId), r.getElo(), r.getPlayed(), r.getWins(), history);
    }

    /** Mes matchs à jouer ou à confirmer, puis les autres. */
    @Transactional(readOnly = true)
    public List<MatchDto> myMatches(AuthUser me) {
        List<Entry> es = entries.ofPlayer(me.id());
        if (es.isEmpty()) {
            return List.of();
        }
        return matches.ofEntries(es.stream().map(Entry::getId).toList()).stream()
                .filter(m -> m.getStatus() != MatchStatus.BYE && m.getStatus() != MatchStatus.WAITING)
                .map(m -> toDto(me, m))
                .sorted(Comparator.comparing((MatchDto d) -> !(d.canReport() || d.canConfirm())).thenComparing(d -> d.status() == MatchStatus.CONFIRMED))
                .toList();
    }

    // ---------- conversions ----------

    private List<StandingDto> standings(List<Entry> es, List<Match> ms, Map<UUID, EntryDto> dtos) {
        Map<UUID, int[]> s = new LinkedHashMap<>();
        es.forEach(e -> s.put(e.getId(), new int[5]));
        ms.stream().filter(m -> m.getStatus() == MatchStatus.CONFIRMED).forEach(m -> {
            int[] a = s.get(m.getEntryAId());
            int[] b = s.get(m.getEntryBId());
            a[0]++;
            b[0]++;
            a[3] += m.getScoreA();
            a[4] += m.getScoreB();
            b[3] += m.getScoreB();
            b[4] += m.getScoreA();
            if (m.getWinnerId().equals(m.getEntryAId())) {
                a[1]++;
                b[2]++;
            } else {
                b[1]++;
                a[2]++;
            }
        });
        return s.entrySet().stream()
                .map(e -> new StandingDto(e.getKey(), dtos.get(e.getKey()).name(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4]))
                .sorted(Comparator.comparingInt(StandingDto::wins).reversed().thenComparing(x -> x.pointsAgainst() - x.pointsFor()))
                .toList();
    }

    private Map<UUID, EntryDto> entryDtos(Collection<Entry> es) {
        Map<UUID, Integer> elo = eloOf(es);
        Set<UUID> users = new HashSet<>();
        es.forEach(e -> {
            users.add(e.getPlayer1Id());
            if (e.getPlayer2Id() != null) {
                users.add(e.getPlayer2Id());
            }
        });
        Map<UUID, String> names = userLookup.names(users);
        return es.stream().collect(Collectors.toMap(Entry::getId, e -> new EntryDto(e.getId(),
                e.getPlayer2Id() == null ? names.get(e.getPlayer1Id()) : names.get(e.getPlayer1Id()) + " / " + names.get(e.getPlayer2Id()),
                e.getPlayer2Id() == null ? List.of(e.getPlayer1Id()) : List.of(e.getPlayer1Id(), e.getPlayer2Id()),
                elo.get(e.getId()), e.getSeed())));
    }

    private MatchDto toDto(AuthUser me, Match m) {
        List<Entry> es = new ArrayList<>();
        if (m.getEntryAId() != null) {
            es.add(entries.findById(m.getEntryAId()).orElseThrow());
        }
        if (m.getEntryBId() != null) {
            es.add(entries.findById(m.getEntryBId()).orElseThrow());
        }
        return toDto(me, m, entryDtos(es), tournaments.findById(m.getTournamentId()).map(Tournament::getName).orElse(null));
    }

    private MatchDto toDto(AuthUser me, Match m, Map<UUID, EntryDto> dtos, String tournamentName) {
        EntryDto a = m.getEntryAId() == null ? null : dtos.get(m.getEntryAId());
        EntryDto b = m.getEntryBId() == null ? null : dtos.get(m.getEntryBId());
        boolean inA = a != null && a.playerIds().contains(me.id());
        boolean inB = b != null && b.playerIds().contains(me.id());
        boolean reporterInA = m.getReportedBy() != null && a != null && a.playerIds().contains(m.getReportedBy());
        boolean canConfirm = m.getStatus() == MatchStatus.REPORTED && ((inA && !reporterInA) || (inB && reporterInA));
        return new MatchDto(m.getId(), m.getRound(), m.getPosition(), a, b, m.getScoreA(), m.getScoreB(), m.getStatus(),
                m.getReportedBy(), m.getReportedBy() == null ? null : userLookup.name(m.getReportedBy()), m.getWinnerId(),
                m.getStatus() == MatchStatus.SCHEDULED && (inA || inB), canConfirm, tournamentName);
    }

    private Tournament find(UUID id) {
        return tournaments.findById(id).orElseThrow(() -> ApiException.notFound("Tournoi"));
    }
}
