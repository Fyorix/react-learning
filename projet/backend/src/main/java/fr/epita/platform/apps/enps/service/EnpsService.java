package fr.epita.platform.apps.enps.service;

import fr.epita.platform.apps.enps.dto.EnpsDtos.*;
import fr.epita.platform.apps.enps.entity.*;
import fr.epita.platform.apps.enps.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Anonymat : la participation (qui) et la réponse (quoi + équipe) sont enregistrées séparément, sans lien ni date.
 * Résultats publiés à partir de 5 réponses par périmètre ; verbatims mélangés.
 * Visibilité des résultats : ADMIN à tout moment, salariés après la clôture.
 */
@Service
@RequiredArgsConstructor
public class EnpsService {

    public static final String ENPS_LABEL = "Recommanderiez-vous l'entreprise à un proche comme employeur ? (0 à 10)";
    static final int MIN_RESPONSES = 5;

    private final EnpsTeamRepository teams;
    private final TeamMemberRepository members;
    private final SurveyRepository surveys;
    private final SurveyQuestionRepository questions;
    private final ParticipationRepository participations;
    private final SurveyResponseRepository responses;
    private final SurveyAnswerRepository answers;
    private final UserRepository users;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- équipes ----------

    @Transactional(readOnly = true)
    public List<TeamDto> teams() {
        Map<UUID, List<UUID>> byTeam = members.findAll().stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId, Collectors.mapping(TeamMember::getUserId, Collectors.toList())));
        return teams.findAllByOrderByNameAsc().stream().map(t -> new TeamDto(t.getId(), t.getName(), byTeam.getOrDefault(t.getId(), List.of()))).toList();
    }

    @Transactional
    public TeamDto createTeam(AuthUser me, TeamRequest req) {
        me.requireAdmin();
        if (teams.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TEAM_EXISTS", "Une équipe porte déjà ce nom");
        }
        EnpsTeam t = new EnpsTeam();
        t.setName(req.name().trim());
        teams.saveAndFlush(t);
        return new TeamDto(t.getId(), t.getName(), List.of());
    }

    @Transactional
    public TeamDto setMembers(AuthUser me, UUID teamId, MembersRequest req) {
        me.requireAdmin();
        EnpsTeam t = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Équipe"));
        Set<UUID> wanted = new HashSet<>(req.userIds());
        wanted.forEach(userLookup::require);
        members.findByTeamId(teamId).stream().filter(m -> !wanted.contains(m.getUserId())).forEach(members::delete);
        for (UUID u : wanted) {
            TeamMember m = members.findById(u).orElseGet(TeamMember::new);
            m.setUserId(u);
            m.setTeamId(teamId);
            members.save(m);
        }
        members.flush();
        return new TeamDto(t.getId(), t.getName(), wanted.stream().sorted().toList());
    }

    // ---------- enquêtes ----------

    @Transactional
    public SurveyDto create(AuthUser me, SurveyRequest req) {
        me.requireAdmin();
        if (!req.closesAt().isAfter(req.opensAt())) {
            throw ValidationException.field("closesAt", "doit être postérieure à l'ouverture");
        }
        if (!req.closesAt().isAfter(clock.instant())) {
            throw ValidationException.field("closesAt", "doit être dans le futur");
        }
        Survey s = new Survey();
        s.setTitle(req.title().trim());
        s.setOpensAt(req.opensAt());
        s.setClosesAt(req.closesAt());
        surveys.saveAndFlush(s);
        question(s, 0, ENPS_LABEL, EnpsQuestionType.SCORE_0_10);
        for (int i = 0; i < req.questions().size(); i++) {
            question(s, i + 1, req.questions().get(i).label().trim(), req.questions().get(i).type());
        }
        questions.flush();
        return toDto(me, s);
    }

    private void question(Survey s, int position, String label, EnpsQuestionType type) {
        SurveyQuestion q = new SurveyQuestion();
        q.setSurveyId(s.getId());
        q.setPosition(position);
        q.setLabel(label);
        q.setType(type);
        questions.save(q);
    }

    /** ADMIN : toutes ; salarié : ouvertes, et closes (pour consulter les résultats). */
    @Transactional(readOnly = true)
    public List<SurveyDto> list(AuthUser me) {
        var now = clock.instant();
        return surveys.findAllByOrderByOpensAtDesc().stream()
                .filter(s -> me.isAdmin() || !s.getOpensAt().isAfter(now))
                .map(s -> toDto(me, s)).toList();
    }

    @Transactional(readOnly = true)
    public SurveyDto get(AuthUser me, UUID id) {
        Survey s = survey(id);
        if (!me.isAdmin() && s.getOpensAt().isAfter(clock.instant())) {
            throw ApiException.notFound("Enquête");
        }
        return toDto(me, s);
    }

    /** Réponse anonyme : participation et réponse enregistrées séparément, sans lien entre elles. */
    @Transactional
    public void respond(AuthUser me, UUID id, ResponseRequest req) {
        Survey s = survey(id);
        var now = clock.instant();
        if (now.isBefore(s.getOpensAt()) || !now.isBefore(s.getClosesAt())) {
            throw ApiException.conflict("SURVEY_CLOSED", "L'enquête n'est pas ouverte aux réponses");
        }
        Participation.Key key = new Participation.Key(id, me.id());
        if (participations.existsById(key)) {
            throw ApiException.conflict("ALREADY_ANSWERED", "Vous avez déjà répondu à cette enquête");
        }
        List<SurveyQuestion> qs = questions.findBySurveyIdOrderByPositionAsc(id);
        Map<UUID, SurveyQuestion> byId = qs.stream().collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));
        Map<UUID, AnswerRequest> given = new LinkedHashMap<>();
        var errors = new ValidationException.Collector();
        for (int i = 0; i < req.answers().size(); i++) {
            AnswerRequest a = req.answers().get(i);
            SurveyQuestion q = byId.get(a.questionId());
            errors.check(q != null, "answers[" + i + "].questionId", "question inconnue pour cette enquête");
            errors.check(given.put(a.questionId(), a) == null, "answers[" + i + "].questionId", "question répondue deux fois");
            if (q != null && q.getType() == EnpsQuestionType.SCORE_0_10 && q.getPosition() > 0) {
                errors.check(a.text() == null || a.text().isBlank(), "answers[" + i + "].text", "attendu : une note (score)");
            }
            if (q != null && q.getType() == EnpsQuestionType.TEXT) {
                errors.check(a.score() == null, "answers[" + i + "].score", "attendu : un texte (text)");
            }
        }
        AnswerRequest enps = given.get(qs.get(0).getId());
        errors.check(enps != null && enps.score() != null, "answers", "la question eNPS (0 à 10) est obligatoire");
        errors.throwIfAny();

        participations.saveAndFlush(new Participation(key));
        SurveyResponse r = new SurveyResponse();
        r.setSurveyId(id);
        r.setTeamId(members.findById(me.id()).map(TeamMember::getTeamId).orElse(null));
        responses.save(r);
        given.values().forEach(a -> {
            if (a.score() == null && (a.text() == null || a.text().isBlank())) {
                return;
            }
            SurveyAnswer ans = new SurveyAnswer();
            ans.setResponseId(r.getId());
            ans.setQuestionId(a.questionId());
            ans.setScore(a.score());
            ans.setText(a.text() == null || a.text().isBlank() ? null : a.text().trim());
            answers.save(ans);
        });
        answers.flush();
    }

    // ---------- résultats ----------

    @Transactional(readOnly = true)
    public ResultsDto results(AuthUser me, UUID id, UUID teamId) {
        Survey s = survey(id);
        requireResultsVisible(me, s);
        List<SurveyQuestion> qs = questions.findBySurveyIdOrderByPositionAsc(id);
        List<SurveyResponse> rs = responses.findBySurveyId(id);
        Map<UUID, List<SurveyAnswer>> byResponse = rs.isEmpty() ? Map.of() : answers.findByResponseIdIn(rs.stream().map(SurveyResponse::getId).toList())
                .stream().collect(Collectors.groupingBy(SurveyAnswer::getResponseId));
        Map<UUID, String> teamNames = teams.findAll().stream().collect(Collectors.toMap(EnpsTeam::getId, EnpsTeam::getName));
        ScopeResult global = scope(null, "Toute l'entreprise", rs, byResponse, qs);
        List<ScopeResult> perTeam = teams.findAllByOrderByNameAsc().stream()
                .filter(t -> teamId == null || t.getId().equals(teamId))
                .map(t -> scope(t.getId(), teamNames.get(t.getId()), rs.stream().filter(r -> t.getId().equals(r.getTeamId())).toList(), byResponse, qs))
                .toList();
        return new ResultsDto(id, s.getTitle(), global, perTeam);
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trends(AuthUser me, UUID teamId) {
        var now = clock.instant();
        return surveys.findAllByOrderByOpensAtAsc().stream()
                .filter(s -> me.isAdmin() ? !s.getOpensAt().isAfter(now) : !s.getClosesAt().isAfter(now))
                .map(s -> {
                    UUID enpsQ = questions.findBySurveyIdOrderByPositionAsc(s.getId()).get(0).getId();
                    List<SurveyResponse> rs = responses.findBySurveyId(s.getId()).stream()
                            .filter(r -> teamId == null || teamId.equals(r.getTeamId())).toList();
                    List<Integer> scores = rs.isEmpty() ? List.of() : answers.findByResponseIdIn(rs.stream().map(SurveyResponse::getId).toList()).stream()
                            .filter(a -> a.getQuestionId().equals(enpsQ) && a.getScore() != null).map(SurveyAnswer::getScore).toList();
                    return new TrendPoint(s.getId(), s.getTitle(), s.getClosesAt(), rs.size(), rs.size() < MIN_RESPONSES ? null : enps(scores));
                }).toList();
    }

    /** Relance : salariés actifs n'ayant pas répondu — jamais le contenu des réponses. */
    @Transactional(readOnly = true)
    public List<NonRespondentDto> nonRespondents(AuthUser me, UUID id) {
        me.requireAdmin();
        survey(id);
        Set<UUID> answered = participations.findByIdSurveyId(id).stream().map(p -> p.getId().getUserId()).collect(Collectors.toSet());
        Map<UUID, UUID> teamOf = members.findAll().stream().collect(Collectors.toMap(TeamMember::getUserId, TeamMember::getTeamId));
        Map<UUID, String> teamNames = teams.findAll().stream().collect(Collectors.toMap(EnpsTeam::getId, EnpsTeam::getName));
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().filter(u -> !answered.contains(u.getId()))
                .map(u -> new NonRespondentDto(u.getId(), u.getDisplayName(), teamOf.containsKey(u.getId()) ? teamNames.get(teamOf.get(u.getId())) : null))
                .toList();
    }

    static int enps(List<Integer> scores) {
        if (scores.isEmpty()) {
            return 0;
        }
        long promoters = scores.stream().filter(s -> s >= 9).count();
        long detractors = scores.stream().filter(s -> s <= 6).count();
        return (int) Math.round((promoters - detractors) * 100.0 / scores.size());
    }

    private ScopeResult scope(UUID teamId, String name, List<SurveyResponse> rs, Map<UUID, List<SurveyAnswer>> byResponse, List<SurveyQuestion> qs) {
        if (rs.size() < MIN_RESPONSES) {
            return new ScopeResult(teamId, name, rs.size(), true, null, null, null, null, List.of(), List.of());
        }
        List<SurveyAnswer> all = rs.stream().flatMap(r -> byResponse.getOrDefault(r.getId(), List.of()).stream()).toList();
        List<Integer> enpsScores = all.stream().filter(a -> a.getQuestionId().equals(qs.get(0).getId()) && a.getScore() != null).map(SurveyAnswer::getScore).toList();
        int n = Math.max(enpsScores.size(), 1);
        List<ScoreResult> scores = new ArrayList<>();
        List<TextResult> texts = new ArrayList<>();
        for (SurveyQuestion q : qs) {
            List<SurveyAnswer> mine = all.stream().filter(a -> a.getQuestionId().equals(q.getId())).toList();
            if (q.getType() == EnpsQuestionType.SCORE_0_10) {
                Map<Integer, Long> dist = new TreeMap<>();
                for (int i = 0; i <= 10; i++) {
                    dist.put(i, 0L);
                }
                mine.stream().map(SurveyAnswer::getScore).filter(Objects::nonNull).forEach(sc -> dist.merge(sc, 1L, Long::sum));
                double avg = mine.stream().map(SurveyAnswer::getScore).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
                scores.add(new ScoreResult(q.getId(), q.getLabel(), Math.round(avg * 10) / 10.0, dist));
            } else {
                List<String> verbatims = new ArrayList<>(mine.stream().map(SurveyAnswer::getText).filter(Objects::nonNull).toList());
                Collections.shuffle(verbatims);
                texts.add(new TextResult(q.getId(), q.getLabel(), verbatims));
            }
        }
        long promoters = enpsScores.stream().filter(s -> s >= 9).count();
        long detractors = enpsScores.stream().filter(s -> s <= 6).count();
        return new ScopeResult(teamId, name, rs.size(), false, enps(enpsScores), (int) Math.round(promoters * 100.0 / n),
                (int) Math.round((enpsScores.size() - promoters - detractors) * 100.0 / n), (int) Math.round(detractors * 100.0 / n), scores, texts);
    }

    private void requireResultsVisible(AuthUser me, Survey s) {
        if (!me.isAdmin() && s.getClosesAt().isAfter(clock.instant())) {
            throw ApiException.forbidden("Les résultats sont publiés à la clôture de l'enquête");
        }
    }

    private Survey survey(UUID id) {
        return surveys.findById(id).orElseThrow(() -> ApiException.notFound("Enquête"));
    }

    private SurveyDto toDto(AuthUser me, Survey s) {
        var now = clock.instant();
        String state = now.isBefore(s.getOpensAt()) ? "UPCOMING" : now.isBefore(s.getClosesAt()) ? "OPEN" : "CLOSED";
        List<QuestionDto> qs = questions.findBySurveyIdOrderByPositionAsc(s.getId()).stream()
                .map(q -> new QuestionDto(q.getId(), q.getPosition(), q.getLabel(), q.getType(), q.getPosition() == 0)).toList();
        return new SurveyDto(s.getId(), s.getTitle(), s.getOpensAt(), s.getClosesAt(), state,
                participations.existsById(new Participation.Key(s.getId(), me.id())), participations.countByIdSurveyId(s.getId()), qs);
    }
}
