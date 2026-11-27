package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.volunteering.dto.VolunteeringDtos.*;
import fr.epita.platform.apps.volunteering.entity.VolunteerStatus;
import fr.epita.platform.apps.volunteering.service.VolunteeringService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolunteeringServiceTest extends IntegrationTest {

    @Autowired
    VolunteeringService service;
    @Autowired
    ManagerRelations relations;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser manager;
    AuthUser alice;
    AuthUser bob;
    AssociationDto restos;
    LocalDate day;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        manager = auth(user("Maya Manager"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        relations.assign(VolunteeringService.REPORTS, alice.id(), manager.id());
        restos = service.addAssociation(admin, new AssociationRequest("Restos " + System.nanoTime(), "Solidarité", true));
        // mi-année suivante : toutes les missions du test tombent la même année
        day = LocalDate.of(LocalDate.now().getYear() + 1, 6, 10);
    }

    private MissionDto mission(int hours, int seats, int dayOffset) {
        return service.createMission(admin, new MissionRequest(restos.id(), "Collecte", day.plusDays(dayOffset), hours, "Paris", List.of("logistique"), seats));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void registrationNeedsManagerApprovalAndSeats() {
        MissionDto m = mission(4, 1, 0);
        RegistrationDto r = service.register(alice, m.id());
        assertThat(r.status()).isEqualTo(VolunteerStatus.REQUESTED);
        assertCode(() -> service.register(alice, m.id()), "ALREADY_REGISTERED");
        assertCode(() -> service.register(bob, m.id()), "MISSION_FULL");
        assertCode(() -> service.approve(bob, r.id()), "FORBIDDEN");
        assertThat(service.team(manager, null)).hasSize(1);
        assertThat(service.approve(manager, r.id()).status()).isEqualTo(VolunteerStatus.APPROVED);
        service.unregister(alice, m.id());
        assertThat(service.register(bob, m.id()).status()).isEqualTo(VolunteerStatus.REQUESTED);
    }

    @Test
    void yearlyLimitOfFourteenHours() {
        service.register(alice, mission(7, 5, 0).id());
        service.register(alice, mission(6, 5, 1).id());
        MissionDto third = mission(2, 5, 2);
        assertCode(() -> service.register(alice, third.id()), "YEARLY_LIMIT_REACHED");
        assertThat(service.register(alice, mission(1, 5, 3).id()).status()).isEqualTo(VolunteerStatus.REQUESTED);
    }

    @Test
    void hoursAreDeclaredAfterTheMissionAndCappedAtItsDuration() {
        MissionDto m = mission(4, 5, 0);
        RegistrationDto r = service.register(alice, m.id());
        service.approve(manager, r.id());
        assertCode(() -> service.declareHours(alice, r.id(), new HoursRequest(new BigDecimal("3"), null)), "MISSION_NOT_DONE");
        jdbc.update("update volunteering_missions set mission_date = current_date - 1 where id = ?", m.id());
        assertThatThrownBy(() -> service.declareHours(alice, r.id(), new HoursRequest(new BigDecimal("5"), null))).isInstanceOf(ValidationException.class);
        RegistrationDto done = service.declareHours(alice, r.id(), new HoursRequest(new BigDecimal("3.5"), "Top"));
        assertThat(done.hours()).isEqualByComparingTo("3.5");
        ReportDto report = service.report(admin, LocalDate.now().minusDays(1).getYear());
        assertThat(report.totalHours()).isEqualByComparingTo("3.5");
        assertThat(report.byAssociation().get(0).label()).isEqualTo(restos.name());
        assertThat(service.me(alice).declaredHours()).isEqualByComparingTo("3.5");
    }

    @Test
    void inactiveAssociationsOfferNoMission() {
        MissionDto m = mission(3, 5, 0);
        service.updateAssociation(admin, restos.id(), new AssociationRequest(restos.name(), "Solidarité", false));
        assertThat(service.missions(alice, null, null, null)).isEmpty();
        assertCode(() -> service.register(alice, m.id()), "MISSION_CLOSED");
        assertCode(() -> service.createMission(admin, new MissionRequest(restos.id(), "X", day, 2, "Lyon", List.of(), 3)), "ASSOCIATION_INACTIVE");
    }
}
