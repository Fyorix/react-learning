package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.birthday.dto.BirthdayDtos.*;
import fr.epita.platform.apps.birthday.entity.EventType;
import fr.epita.platform.apps.birthday.service.BirthdayService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthdayServiceTest extends IntegrationTest {

    @Autowired
    BirthdayService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    LocalDate today;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        today = LocalDate.now(ZoneId.of("Europe/Paris"));
    }

    @Test
    void leapDayBirthdaysAreCelebratedOnTheTwentyEighth() {
        service.save(admin, alice.id(), new PersonRequest(LocalDate.of(1992, 2, 29), LocalDate.of(2020, 3, 1), null));
        // année non bissextile
        int year = 2027;
        assertThat(service.calendar(YearMonth.of(year, 2), null)).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(LocalDate.of(year, 2, 28));
            assertThat(e.type()).isEqualTo(EventType.BIRTHDAY);
            assertThat(e.years()).isNull();
        });
        assertThat(service.calendar(YearMonth.of(2028, 2), null).get(0).date()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void seniorityAndMilestonesAndHiddenBirthdays() {
        LocalDate hire = today.minusYears(5);
        service.save(admin, alice.id(), new PersonRequest(today.minusYears(30), hire, null));
        service.preferences(alice, false);
        RemindersDto r = service.reminders();
        // la naissance est masquée, l'ancienneté reste visible
        assertThat(r.todayEvents()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(EventType.ANNIVERSARY);
            assertThat(e.years()).isEqualTo(5);
            assertThat(e.milestone()).isTrue();
        });
        MeDto me = service.me(alice);
        assertThat(me.birthDate()).isEqualTo(today.minusYears(30));
        assertThat(service.people(admin).stream().filter(p -> p.userId().equals(alice.id())).findFirst().orElseThrow().birthDay())
                .matches("\\d{2}/\\d{2}");
    }

    @Test
    void upcomingCrossesTheYearAndRemindersIncludeTomorrow() {
        service.save(admin, bob.id(), new PersonRequest(today.plusDays(1).minusYears(25), today.minusYears(2).plusDays(1), null));
        RemindersDto r = service.reminders();
        assertThat(r.tomorrowEvents()).extracting(EventDto::type).containsExactlyInAnyOrder(EventType.BIRTHDAY, EventType.ANNIVERSARY);
        assertThat(r.todayEvents()).isEmpty();
        assertThat(service.upcoming(366, null)).extracting(EventDto::type).contains(EventType.BIRTHDAY, EventType.ANNIVERSARY);
        assertThatThrownBy(() -> service.upcoming(0, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.save(admin, bob.id(), new PersonRequest(null, today.plusDays(3), null))).isInstanceOf(ValidationException.class);
    }

    @Test
    void csvImportReportsInvalidLinesAndImportsTheOthers() {
        String csv = "email;birthDate;hireDate;team\n"
                + alice.email() + ";1990-05-12;2019-09-02;Marketing\n"
                + "inconnu@example.com;1990-05-12;2019-09-02;Marketing\n"
                + bob.email() + ";12/05/1990;2019-09-02;Ventes\n"
                + bob.email() + ";;2018-01-15;Ventes\n";
        ImportReport report = service.importCsv(admin, new MockMultipartFile("file", "fiches.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.rejected()).isEqualTo(2);
        assertThat(report.lines()).extracting(ImportLine::line).containsExactly(2, 3, 4, 5);
        assertThat(service.teams()).extracting(TeamDto::name).containsExactlyInAnyOrder("Marketing", "Ventes");
    }
}
