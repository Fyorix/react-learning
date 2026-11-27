package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DayMenuDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DayMenuRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.GenerateRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.WeekDto;
import fr.epita.platform.apps.canteen.entity.DishCategory;
import fr.epita.platform.apps.canteen.entity.WeekStatus;
import fr.epita.platform.apps.canteen.service.CanteenService;
import fr.epita.platform.apps.canteen.service.IsoWeek;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanteenServiceTest extends IntegrationTest {

    @Autowired
    CanteenService service;

    AuthUser admin;
    AuthUser alice;
    String nextWeek;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        nextWeek = IsoWeek.of(LocalDate.now().plusWeeks(1));
    }

    private DishDto dish(String name, DishCategory c, boolean veg) {
        return service.createDish(admin, new DishRequest(name, c, null, veg, List.of()));
    }

    private void catalog(int perCategory, int vegMains) {
        for (int i = 0; i < perCategory; i++) {
            dish("Entrée " + i, DishCategory.STARTER, true);
            dish("Plat " + i, DishCategory.MAIN, i < vegMains);
            dish("Dessert " + i, DishCategory.DESSERT, true);
        }
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void generationFillsTheWeekWithoutRepetitionWhenPossible() {
        catalog(6, 2);
        // tirage aléatoire : plusieurs essais pour couvrir les ordres défavorables (jours végétariens en fin de semaine)
        for (int run = 0; run < 20; run++) {
            WeekDto week = service.generate(admin, nextWeek, new GenerateRequest(true, 2));
            assertThat(week.days()).allMatch(DayMenuDto::complete);
            assertThat(week.days().stream().map(d -> d.main().id()).collect(Collectors.toSet())).hasSize(5);
            assertThat(week.days().stream().map(d -> d.starter().id()).collect(Collectors.toSet())).hasSize(5);
            assertThat(week.days().stream().filter(d -> d.main().vegetarian()).count()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void smallCatalogAllowsRepetitionButNeverTwoDaysInARow() {
        catalog(2, 1);
        for (int run = 0; run < 10; run++) {
            WeekDto week = service.generate(admin, nextWeek, new GenerateRequest(true, 0));
            for (int i = 1; i < week.days().size(); i++) {
                assertThat(week.days().get(i).main().id()).isNotEqualTo(week.days().get(i - 1).main().id());
                assertThat(week.days().get(i).dessert().id()).isNotEqualTo(week.days().get(i - 1).dessert().id());
            }
        }
    }

    @Test
    void generationKeepsManualDaysUnlessOverwrite() {
        catalog(6, 1);
        Map<DishCategory, DishDto> first = service.dishes(null, null, null).stream()
                .collect(Collectors.toMap(DishDto::category, d -> d, (a, b) -> a));
        service.setDay(admin, nextWeek, "MONDAY", new DayMenuRequest(first.get(DishCategory.STARTER).id(),
                first.get(DishCategory.MAIN).id(), first.get(DishCategory.DESSERT).id()));
        WeekDto week = service.generate(admin, nextWeek, new GenerateRequest(false, 0));
        assertThat(week.days().getFirst().main().id()).isEqualTo(first.get(DishCategory.MAIN).id());
        assertThat(week.days().stream().skip(1).map(d -> d.main().id()))
                .doesNotContain(first.get(DishCategory.MAIN).id());
    }

    @Test
    void emptyCategoryIsReported() {
        dish("Salade", DishCategory.STARTER, true);
        dish("Steak", DishCategory.MAIN, false);
        assertCode(() -> service.generate(admin, nextWeek, null), "CATALOG_INCOMPLETE");
    }

    @Test
    void dishCategoryIsChecked() {
        catalog(1, 1);
        DishDto starter = service.dishes(DishCategory.STARTER, null, null).getFirst();
        assertThatThrownBy(() -> service.setDay(admin, nextWeek, "TUESDAY",
                new DayMenuRequest(starter.id(), starter.id(), starter.id()))).isInstanceOf(ValidationException.class);
    }

    @Test
    void publicationLifecycleAndVisibility() {
        catalog(5, 0);
        service.generate(admin, nextWeek, null);
        assertCode(() -> service.week(alice, nextWeek), "MENU_NOT_PUBLISHED");
        assertThat(service.publish(admin, nextWeek).status()).isEqualTo(WeekStatus.PUBLISHED);
        assertThat(service.week(alice, nextWeek).days()).hasSize(5);
        assertCode(() -> service.generate(admin, nextWeek, new GenerateRequest(true, 0)), "MENU_PUBLISHED");
        service.unpublish(admin, nextWeek);
        service.generate(admin, nextWeek, new GenerateRequest(true, 0));
    }

    @Test
    void dishUsedInAnUpcomingWeekCannotBeDeletedButCanBeArchived() {
        catalog(1, 0);
        WeekDto week = service.generate(admin, nextWeek, null);
        DishDto used = week.days().getFirst().main();
        assertCode(() -> service.deleteDish(admin, used.id()), "DISH_IN_USE");
        assertThat(service.toggleArchive(admin, used.id()).archived()).isTrue();
        assertCode(() -> service.generate(admin, nextWeek, new GenerateRequest(true, 0)), "CATALOG_INCOMPLETE");
    }
}
