package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyRequest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateRequest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateTaskRequest;
import fr.epita.platform.apps.onboarding.entity.TaskOwner;
import fr.epita.platform.apps.onboarding.service.OnboardingService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardingServiceTest extends IntegrationTest {

    @Autowired
    OnboardingService service;

    AuthUser admin;
    AuthUser nina;
    AuthUser marc;
    AuthUser bruno;
    TemplateDto template;
    LocalDate start = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        nina = auth(user("Nina Nouvelle"));
        marc = auth(user("Marc Manager"));
        bruno = auth(user("Bruno Parrain"));
        template = service.createTemplate(admin, new TemplateRequest("Développeur", List.of(
                new TemplateTaskRequest("Préparer le poste", null, TaskOwner.IT, -2, true),
                new TemplateTaskRequest("Déjeuner d'accueil", null, TaskOwner.BUDDY, 0, false),
                new TemplateTaskRequest("Lire la charte", null, TaskOwner.NEWCOMER, 3, true),
                new TemplateTaskRequest("Point d'étonnement", null, TaskOwner.MANAGER, 30, true))));
    }

    private JourneyDto launch() {
        return service.launch(admin, new JourneyRequest(template.id(), nina.id(), marc.id(), bruno.id(), start));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void dueDatesAreComputedFromTheStartDateAndTasksAreCopied() {
        JourneyDto j = launch();
        assertThat(j.tasks()).extracting("dueDate").containsExactly(start.minusDays(2), start, start.plusDays(3), start.plusDays(30));
        service.updateTemplate(admin, template.id(), new TemplateRequest("Développeur", List.of(
                new TemplateTaskRequest("Une seule tâche", null, TaskOwner.NEWCOMER, 1, true))));
        assertThat(service.get(nina, j.id()).tasks()).hasSize(4);
    }

    @Test
    void onlyOneActiveJourneyAndDistinctRoles() {
        launch();
        assertCode(this::launch, "ONBOARDING_ALREADY_ACTIVE");
        assertThatThrownBy(() -> service.launch(admin, new JourneyRequest(template.id(), bruno.id(), marc.id(), marc.id(), start)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void onlyTheOwnerOfATaskTicksIt() {
        JourneyDto j = launch();
        var it = j.tasks().get(0);
        var buddy = j.tasks().get(1);
        var newcomer = j.tasks().get(2);
        assertCode(() -> service.toggle(nina, j.id(), it.id(), true), "FORBIDDEN");
        assertCode(() -> service.toggle(marc, j.id(), newcomer.id(), true), "FORBIDDEN");
        assertThat(service.toggle(nina, j.id(), newcomer.id(), true).done()).isEqualTo(1);
        service.toggle(bruno, j.id(), buddy.id(), true);
        service.toggle(admin, j.id(), it.id(), true);
        JourneyDto almost = service.get(marc, j.id());
        assertThat(almost.status()).isEqualTo("IN_PROGRESS");
        JourneyDto done = service.toggle(marc, j.id(), j.tasks().get(3).id(), true);
        assertThat(done.status()).isEqualTo("COMPLETED");
        // un nouveau parcours est possible une fois le précédent terminé
        assertThat(launch().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void overdueTasksAndVisibility() {
        JourneyDto j = service.launch(admin, new JourneyRequest(template.id(), nina.id(), marc.id(), bruno.id(), LocalDate.now().minusDays(5)));
        assertThat(j.overdue()).isEqualTo(3);
        assertThat(service.list(bruno, null, true)).hasSize(1);
        assertThat(service.list(auth(user("Eve")), null, null)).isEmpty();
        assertCode(() -> service.get(auth(user("Zoé")), j.id()), "NOT_FOUND");
        assertThat(service.mine(nina).id()).isEqualTo(j.id());
    }
}
