package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AnswerRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuestionDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuestionRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.SubmittedAnswer;
import fr.epita.platform.apps.quiz.service.QuizService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizServiceTest extends IntegrationTest {

    @Autowired
    QuizService service;

    private QuizRequest request() {
        return new QuizRequest("React", "Les bases", List.of(
                new QuestionRequest("Qui maintient React ?", List.of(
                        new AnswerRequest("Meta", true), new AnswerRequest("Google", false))),
                new QuestionRequest("Hooks d'état ?", List.of(
                        new AnswerRequest("useState", true), new AnswerRequest("useReducer", true),
                        new AnswerRequest("useMemo", false)))));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void eachQuestionNeedsACorrectAnswer() {
        AuthUser admin = auth(admin("Admin"));
        QuizRequest invalid = new QuizRequest("Q", null, List.of(new QuestionRequest("?", List.of(
                new AnswerRequest("a", false), new AnswerRequest("b", false)))));
        assertThatThrownBy(() -> service.create(admin, invalid)).isInstanceOf(ValidationException.class);
    }

    @Test
    void correctAnswersAreNeverSentToUsers() throws Exception {
        AuthUser admin = auth(admin("Admin"));
        User alice = user("Alice");
        QuizDto quiz = service.create(admin, request());
        service.publish(admin, quiz.id());

        mvc.perform(get("/api/quiz/quizzes/" + quiz.id()).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].answers[0].correct").doesNotExist());
        assertThat(service.get(admin, quiz.id()).questions().getFirst().answers().getFirst().correct()).isTrue();
    }

    @Test
    void unpublishedQuizIsInvisibleToUsers() {
        AuthUser admin = auth(admin("Admin"));
        AuthUser alice = auth(user("Alice"));
        QuizDto quiz = service.create(admin, request());
        assertThat(service.list(alice)).isEmpty();
        assertCode(() -> service.get(alice, quiz.id()), "NOT_FOUND");
    }

    @Test
    void scoringOneAttemptAndPublishedLock() {
        AuthUser admin = auth(admin("Admin"));
        AuthUser alice = auth(user("Alice"));
        QuizDto quiz = service.publish(admin, service.create(admin, request()).id());
        QuestionDto q1 = quiz.questions().get(0);
        QuestionDto q2 = quiz.questions().get(1);

        var result = service.submit(alice, quiz.id(), List.of(
                new SubmittedAnswer(q1.id(), List.of(q1.answers().get(0).id())),
                new SubmittedAnswer(q2.id(), List.of(q2.answers().get(0).id())))); // incomplet → faux
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.corrections().get(1).correctAnswerIds()).hasSize(2);

        assertCode(() -> service.submit(alice, quiz.id(), List.of()), "ALREADY_ATTEMPTED");
        assertCode(() -> service.update(admin, quiz.id(), request()), "QUIZ_PUBLISHED");
        assertCode(() -> service.unpublish(admin, quiz.id()), "QUIZ_HAS_ATTEMPTS");
    }

    @Test
    void leaderboardOrdersByScoreThenDate() {
        AuthUser admin = auth(admin("Admin"));
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        AuthUser carol = auth(user("Carol"));
        QuizDto quiz = service.publish(admin, service.create(admin, request()).id());
        QuestionDto q1 = quiz.questions().get(0);
        var right = List.of(new SubmittedAnswer(q1.id(), List.of(q1.answers().get(0).id())));
        var wrong = List.of(new SubmittedAnswer(q1.id(), List.of(q1.answers().get(1).id())));

        service.submit(alice, quiz.id(), wrong);
        service.submit(bob, quiz.id(), right);
        service.submit(carol, quiz.id(), right);

        assertThat(service.leaderboard(alice, quiz.id())).extracting(e -> e.displayName())
                .containsExactly("Bob", "Carol", "Alice");
        assertThat(service.globalLeaderboard().getFirst().displayName()).isEqualTo("Bob");
    }
}
