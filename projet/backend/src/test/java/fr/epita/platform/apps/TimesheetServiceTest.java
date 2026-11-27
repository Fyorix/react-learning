package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.EntryRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectRequest;
import fr.epita.platform.apps.timesheet.entity.WeekStatus;
import fr.epita.platform.apps.timesheet.service.TimesheetService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.IsoWeeks;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimesheetServiceTest extends IntegrationTest {

    @Autowired
    TimesheetService service;
    @Autowired
    ManagerRelations relations;

    AuthUser admin;
    AuthUser marc;
    AuthUser alice;
    ProjectDto acme;
    ProjectDto absence;
    // semaine sans jour férié : 3e semaine de mars de l'année suivante
    String week = IsoWeeks.of(LocalDate.of(LocalDate.now().getYear() + 1, 3, 18));
    LocalDate monday = IsoWeeks.monday(week);

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        marc = auth(user("Marc"));
        alice = auth(user("Alice"));
        relations.assign(TimesheetService.REPORTS, alice.id(), marc.id());
        acme = service.createProject(admin, new ProjectRequest("ACME", "Site Acme", "Acme", null));
        absence = service.projects(null).stream().filter(p -> p.code().equals("ABSENCE")).findFirst().orElseThrow();
    }

    private List<EntryRequest> fullWeek(String hours) {
        List<EntryRequest> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(new EntryRequest(acme.id(), monday.plusDays(i), new BigDecimal(hours)));
        }
        list.add(new EntryRequest(absence.id(), monday.plusDays(4), new BigDecimal("7")));
        return list;
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void quarterHoursAndTenHoursPerDayMax() {
        assertThatThrownBy(() -> service.save(alice, week, List.of(new EntryRequest(acme.id(), monday, new BigDecimal("1.10")))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.save(alice, week, List.of(new EntryRequest(acme.id(), monday, new BigDecimal("6")),
                new EntryRequest(absence.id(), monday, new BigDecimal("4.5"))))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.save(alice, week, List.of(new EntryRequest(acme.id(), monday.plusDays(7), BigDecimal.ONE))))
                .isInstanceOf(ValidationException.class);
        assertThat(service.save(alice, week, fullWeek("7.75")).total()).isEqualByComparingTo("38");
    }

    @Test
    void submitRequiresEveryWorkingDayAndLocksTheWeek() {
        service.save(alice, week, fullWeek("7").subList(0, 3));
        assertCode(() -> service.submit(alice, week), "WEEK_INCOMPLETE");
        service.save(alice, week, fullWeek("7"));
        assertThat(service.submit(alice, week).status()).isEqualTo(WeekStatus.SUBMITTED);
        assertCode(() -> service.save(alice, week, fullWeek("8")), "WEEK_LOCKED");
    }

    @Test
    void closedProjectsRefuseHours() {
        service.updateProject(admin, acme.id(), new ProjectRequest("ACME", "Site Acme", "Acme", "CLOSED"));
        assertCode(() -> service.save(alice, week, fullWeek("7")), "PROJECT_CLOSED");
    }

    @Test
    void managerApprovesOrReturnsWithAComment() {
        service.save(alice, week, fullWeek("7"));
        service.submit(alice, week);
        assertCode(() -> service.approve(auth(user("Bob")), week, alice.id()), "FORBIDDEN");
        assertThatThrownBy(() -> service.sendBack(marc, week, alice.id(), " ")).isInstanceOf(ValidationException.class);
        assertThat(service.sendBack(marc, week, alice.id(), "Mercredi incomplet").status()).isEqualTo(WeekStatus.RETURNED);
        assertThat(service.save(alice, week, fullWeek("7.5")).editable()).isTrue();
        service.submit(alice, week);
        assertThat(service.approve(marc, week, alice.id()).status()).isEqualTo(WeekStatus.APPROVED);
        assertCode(() -> service.approve(marc, week, alice.id()), "INVALID_STATUS_TRANSITION");
        var report = service.report(marc, monday, monday.plusDays(6), null, null);
        assertThat(report).extracting("projectCode").containsExactly("ABSENCE", "ACME");
        assertThat(report.get(1).hours()).isEqualByComparingTo("30");
    }

    @Test
    void missingWeeksListsPastUnsubmittedWeeks() {
        assertThat(service.missing(alice)).hasSize(8);
    }
}
