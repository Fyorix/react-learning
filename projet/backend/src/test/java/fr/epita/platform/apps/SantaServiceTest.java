package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.ExclusionDto;
import fr.epita.platform.apps.santa.entity.InvitationStatus;
import fr.epita.platform.apps.santa.service.SantaDraw;
import fr.epita.platform.apps.santa.service.SantaService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SantaServiceTest extends IntegrationTest {

    @Autowired
    SantaService service;

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private EventDto eventWith(AuthUser organizer, List<AuthUser> guests) {
        EventDto e = service.create(organizer, new EventRequest("Noël", LocalDate.now().plusDays(30),
                BigDecimal.valueOf(20), LocalDate.now().plusDays(20)));
        service.invite(organizer, e.id(), guests.stream().map(AuthUser::id).toList());
        guests.forEach(g -> service.respond(g, e.id(), InvitationStatus.ACCEPTED));
        return e;
    }

    @Test
    void drawAlgorithmRespectsAllConstraints() {
        Random random = new Random(42);
        for (int n = 3; n <= 12; n++) {
            List<UUID> people = IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
            Set<SantaDraw.Pair> excluded = Set.of(new SantaDraw.Pair(people.get(0), people.get(1)));
            Map<UUID, UUID> result = SantaDraw.draw(people, excluded, random).orElseThrow();
            assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(people);
            assertThat(new HashSet<>(result.values())).containsExactlyInAnyOrderElementsOf(people);
            result.forEach((giver, receiver) -> {
                assertThat(receiver).isNotEqualTo(giver);
                if (people.size() > 3) {
                    assertThat(result.get(receiver)).isNotEqualTo(giver);
                }
            });
            assertThat(result.get(people.get(0))).isNotEqualTo(people.get(1));
        }
    }

    @Test
    void drawNeedsThreeParticipantsAndFailsWhenImpossible() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        EventDto small = eventWith(org, List.of(a));
        assertCode(() -> service.draw(org, small.id()), "NOT_ENOUGH_PARTICIPANTS");

        AuthUser b = auth(user("Bruno"));
        EventDto e = eventWith(org, List.of(a, b));
        // avec 3 personnes, org → a est la seule autre option si org ne doit pas offrir à b ;
        // on interdit aussi org → a : plus aucune combinaison
        service.setExclusions(org, e.id(), List.of(new ExclusionDto(org.id(), a.id()), new ExclusionDto(org.id(), b.id())));
        assertCode(() -> service.draw(org, e.id()), "DRAW_IMPOSSIBLE");
    }

    @Test
    void eachParticipantOnlySeesWhoHeOffersTo() throws Exception {
        var orgUser = user("Organisateur");
        AuthUser org = auth(orgUser);
        AuthUser a = auth(user("Anna"));
        AuthUser b = auth(user("Bruno"));
        AuthUser c = auth(user("Chloe"));
        EventDto e = eventWith(org, List.of(a, b, c));
        service.setWishlist(a, e.id(), List.of("Un livre", "Du thé"));
        service.draw(org, e.id());

        List<UUID> receivers = new ArrayList<>();
        for (AuthUser p : List.of(org, a, b, c)) {
            receivers.add(service.myAssignment(p, e.id()).receiverId());
        }
        assertThat(new HashSet<>(receivers)).hasSize(4);

        // le détail ne contient aucune attribution, même pour l'organisateur
        mvc.perform(get("/api/santa/events/" + e.id()).header("Authorization", bearer(orgUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").doesNotExist())
                .andExpect(jsonPath("$.invitations[0].receiverId").doesNotExist());

        assertCode(() -> service.invite(org, e.id(), List.of(UUID.randomUUID())), "DRAW_ALREADY_DONE");
    }

    @Test
    void recipientSeesGiftReadyWithoutKnowingTheSanta() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        AuthUser b = auth(user("Bruno"));
        EventDto e = eventWith(org, List.of(a, b));
        service.draw(org, e.id());
        UUID receiver = service.myAssignment(a, e.id()).receiverId();
        AuthUser receiverAuth = List.of(org, b).stream().filter(u -> u.id().equals(receiver)).findFirst().orElseThrow();

        assertThat(service.myGift(receiverAuth, e.id()).ready()).isFalse();
        service.markReady(a, e.id());
        assertThat(service.myGift(receiverAuth, e.id()).ready()).isTrue();
    }

    @Test
    void cancellingTheDrawUnfreezesTheList() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        AuthUser b = auth(user("Bruno"));
        EventDto e = eventWith(org, List.of(a, b));
        service.draw(org, e.id());
        assertCode(() -> service.cancelDraw(a, e.id()), "FORBIDDEN");
        service.cancelDraw(org, e.id());
        assertCode(() -> service.myAssignment(a, e.id()), "NOT_FOUND");
        service.invite(org, e.id(), List.of(auth(user("Chloe")).id()));
    }
}
