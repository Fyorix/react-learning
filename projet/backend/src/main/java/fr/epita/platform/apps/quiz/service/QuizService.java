package fr.epita.platform.apps.quiz.service;

import fr.epita.platform.apps.quiz.dto.QuizDtos.AnswerDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AnswerRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AttemptDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AttemptResultDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.CorrectionDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.LeaderboardEntry;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuestionDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuestionRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizSummaryDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.SubmittedAnswer;
import fr.epita.platform.apps.quiz.entity.Answer;
import fr.epita.platform.apps.quiz.entity.Attempt;
import fr.epita.platform.apps.quiz.entity.Question;
import fr.epita.platform.apps.quiz.entity.Quiz;
import fr.epita.platform.apps.quiz.repository.AttemptRepository;
import fr.epita.platform.apps.quiz.repository.QuizRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizzes;
    private final AttemptRepository attempts;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<QuizSummaryDto> list(AuthUser me) {
        Map<UUID, Attempt> mine = attempts.findByUserIdOrderBySubmittedAtDesc(me.id()).stream()
                .collect(Collectors.toMap(Attempt::getQuizId, a -> a));
        return quizzes.findAllByOrderByCreatedAtDesc().stream()
                .filter(q -> me.isAdmin() || q.isPublished())
                .map(q -> {
                    Attempt a = mine.get(q.getId());
                    return new QuizSummaryDto(q.getId(), q.getTitle(), q.getDescription(), q.isPublished(),
                            q.getQuestions().size(), attempts.countByQuizId(q.getId()), a != null,
                            a == null ? null : a.getScore(), q.getCreatedAt());
                }).toList();
    }

    /** Les bonnes réponses ne sont jamais renvoyées à un USER. */
    @Transactional(readOnly = true)
    public QuizDto get(AuthUser me, UUID id) {
        Quiz quiz = find(id);
        if (!me.isAdmin() && !quiz.isPublished()) {
            throw ApiException.notFound("Quiz");
        }
        return toDto(quiz, me);
    }

    @Transactional
    public QuizDto create(AuthUser me, QuizRequest req) {
        me.requireAdmin();
        validate(req);
        Quiz quiz = new Quiz();
        apply(quiz, req);
        return toDto(quizzes.saveAndFlush(quiz), me);
    }

    @Transactional
    public QuizDto update(AuthUser me, UUID id, QuizRequest req) {
        me.requireAdmin();
        Quiz quiz = find(id);
        if (quiz.isPublished()) {
            throw ApiException.conflict("QUIZ_PUBLISHED", "Un quiz publié n'est plus modifiable");
        }
        validate(req);
        apply(quiz, req);
        return toDto(quizzes.saveAndFlush(quiz), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        me.requireAdmin();
        Quiz quiz = find(id);
        requireNoAttempt(id);
        quizzes.delete(quiz);
    }

    @Transactional
    public QuizDto publish(AuthUser me, UUID id) {
        me.requireAdmin();
        Quiz quiz = find(id);
        if (!quiz.isPublished()) {
            quiz.setPublished(true);
            quiz.setPublishedAt(clock.instant());
        }
        return toDto(quiz, me);
    }

    @Transactional
    public QuizDto unpublish(AuthUser me, UUID id) {
        me.requireAdmin();
        Quiz quiz = find(id);
        requireNoAttempt(id);
        quiz.setPublished(false);
        quiz.setPublishedAt(null);
        return toDto(quiz, me);
    }

    /** Une seule tentative ; 1 point par question entièrement juste. La correction n'est renvoyée qu'ici. */
    @Transactional
    public AttemptResultDto submit(AuthUser me, UUID id, List<SubmittedAnswer> submitted) {
        Quiz quiz = find(id);
        if (!quiz.isPublished()) {
            throw ApiException.notFound("Quiz");
        }
        if (attempts.existsByQuizIdAndUserId(id, me.id())) {
            throw ApiException.conflict("ALREADY_ATTEMPTED", "Vous avez déjà répondu à ce quiz");
        }
        Map<UUID, Set<UUID>> selected = new HashMap<>();
        for (SubmittedAnswer s : submitted) {
            selected.put(s.questionId(), new HashSet<>(s.answerIds()));
        }
        Set<UUID> questionIds = quiz.getQuestions().stream().map(Question::getId).collect(Collectors.toSet());
        if (!questionIds.containsAll(selected.keySet())) {
            throw ApiException.badRequest("INVALID_ATTEMPT", "Question inconnue pour ce quiz");
        }
        List<CorrectionDto> corrections = new ArrayList<>();
        int score = 0;
        for (Question q : quiz.getQuestions()) {
            Set<UUID> correct = q.getAnswers().stream().filter(Answer::isCorrect).map(Answer::getId)
                    .collect(Collectors.toSet());
            Set<UUID> chosen = selected.getOrDefault(q.getId(), Set.of());
            boolean ok = correct.equals(chosen);
            if (ok) {
                score++;
            }
            corrections.add(new CorrectionDto(q.getId(), q.getLabel(), ordered(q, correct), ordered(q, chosen), ok));
        }
        Attempt attempt = new Attempt();
        attempt.setQuizId(id);
        attempt.setUserId(me.id());
        attempt.setScore(score);
        attempt.setTotal(quiz.getQuestions().size());
        attempt.setSubmittedAt(clock.instant());
        attempt = attempts.saveAndFlush(attempt);
        return new AttemptResultDto(attempt.getId(), id, quiz.getTitle(), score, attempt.getTotal(),
                attempt.getSubmittedAt(), corrections);
    }

    @Transactional(readOnly = true)
    public List<AttemptDto> myAttempts(AuthUser me) {
        List<Attempt> list = attempts.findByUserIdOrderBySubmittedAtDesc(me.id());
        Map<UUID, String> titles = quizzes.findAllById(list.stream().map(Attempt::getQuizId).toList()).stream()
                .collect(Collectors.toMap(Quiz::getId, Quiz::getTitle));
        return list.stream().map(a -> new AttemptDto(a.getId(), a.getQuizId(), titles.get(a.getQuizId()),
                a.getScore(), a.getTotal(), a.getSubmittedAt())).toList();
    }

    /** Score décroissant, puis date de tentative croissante. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(AuthUser me, UUID quizId) {
        Quiz quiz = find(quizId);
        if (!me.isAdmin() && !quiz.isPublished()) {
            throw ApiException.notFound("Quiz");
        }
        List<Attempt> list = attempts.findByQuizIdOrderByScoreDescSubmittedAtAsc(quizId);
        Map<UUID, String> names = userLookup.namesOf(list, Attempt::getUserId);
        List<LeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Attempt a = list.get(i);
            result.add(new LeaderboardEntry(i + 1, a.getUserId(), names.get(a.getUserId()), a.getScore(), a.getTotal(),
                    1, a.getSubmittedAt()));
        }
        return result;
    }

    /** Classement général : somme des scores ; à égalité, la première tentative la plus ancienne. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> globalLeaderboard() {
        Map<UUID, List<Attempt>> byUser = attempts.findAll().stream().collect(Collectors.groupingBy(Attempt::getUserId));
        Map<UUID, String> names = userLookup.names(byUser.keySet());
        record Row(UUID userId, int score, int total, int count, Instant first) {
        }
        List<Row> rows = byUser.entrySet().stream().map(e -> new Row(e.getKey(),
                        e.getValue().stream().mapToInt(Attempt::getScore).sum(),
                        e.getValue().stream().mapToInt(Attempt::getTotal).sum(),
                        e.getValue().size(),
                        e.getValue().stream().map(Attempt::getSubmittedAt).min(Comparator.naturalOrder()).orElseThrow()))
                .sorted(Comparator.comparingInt(Row::score).reversed().thenComparing(Row::first))
                .toList();
        List<LeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            result.add(new LeaderboardEntry(i + 1, r.userId(), names.get(r.userId()), r.score(), r.total(), r.count(),
                    r.first()));
        }
        return result;
    }

    private void validate(QuizRequest req) {
        var collector = new ValidationException.Collector();
        for (int i = 0; i < req.questions().size(); i++) {
            QuestionRequest q = req.questions().get(i);
            collector.check(q.answers().stream().anyMatch(AnswerRequest::correct), "questions[" + i + "].answers",
                    "Chaque question doit avoir au moins une bonne réponse");
        }
        collector.throwIfAny();
    }

    private void apply(Quiz quiz, QuizRequest req) {
        quiz.setTitle(req.title().trim());
        quiz.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        quiz.getQuestions().clear();
        int qPos = 0;
        for (QuestionRequest qr : req.questions()) {
            Question q = new Question();
            q.setQuiz(quiz);
            q.setLabel(qr.label().trim());
            q.setPosition(qPos++);
            int aPos = 0;
            for (AnswerRequest ar : qr.answers()) {
                Answer a = new Answer();
                a.setQuestion(q);
                a.setLabel(ar.label().trim());
                a.setCorrect(ar.correct());
                a.setPosition(aPos++);
                q.getAnswers().add(a);
            }
            quiz.getQuestions().add(q);
        }
    }

    private void requireNoAttempt(UUID id) {
        if (attempts.existsByQuizId(id)) {
            throw ApiException.conflict("QUIZ_HAS_ATTEMPTS", "Des tentatives existent déjà pour ce quiz");
        }
    }

    private static List<UUID> ordered(Question q, Set<UUID> ids) {
        return q.getAnswers().stream().map(Answer::getId).filter(ids::contains).toList();
    }

    private Quiz find(UUID id) {
        return quizzes.findById(id).orElseThrow(() -> ApiException.notFound("Quiz"));
    }

    private QuizDto toDto(Quiz quiz, AuthUser me) {
        boolean admin = me.isAdmin();
        List<QuestionDto> questions = quiz.getQuestions().stream().map(q -> new QuestionDto(q.getId(), q.getLabel(),
                q.getAnswers().stream().filter(Answer::isCorrect).count() > 1,
                q.getAnswers().stream().map(a -> new AnswerDto(a.getId(), a.getLabel(),
                        admin ? Boolean.valueOf(a.isCorrect()) : null)).toList())).toList();
        return new QuizDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(), quiz.isPublished(),
                quiz.getPublishedAt(), quiz.getQuestions().size(), attempts.countByQuizId(quiz.getId()),
                attempts.existsByQuizIdAndUserId(quiz.getId(), me.id()), questions);
    }
}
