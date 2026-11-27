package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.MessageRequest;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingStatus;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingType;
import fr.epita.platform.apps.classified.service.ClassifiedService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassifiedServiceTest extends IntegrationTest {

    @Autowired
    ClassifiedService service;

    AuthUser alice;
    AuthUser bob;
    AuthUser admin;
    User aliceUser;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        aliceUser = user("Alice");
        alice = auth(aliceUser);
        bob = auth(user("Bob"));
    }

    private static List<MultipartFile> photos(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> (MultipartFile) new MockMultipartFile("photos", "p" + i + ".png", "image/png", new byte[]{1, 2, (byte) i})).toList();
    }

    private ListingDto sell(String title) {
        return service.publish(alice, new ListingRequest(ListingType.SALE, title, "Très bon état", "Sport", new BigDecimal("50")), photos(2));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void priceDependsOnTypeAndPhotosAreLimited() {
        assertThatThrownBy(() -> service.publish(alice, new ListingRequest(ListingType.SALE, "Vélo", "x", "Sport", null), photos(1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.publish(alice, new ListingRequest(ListingType.GIFT, "Livres", "x", "Livres", BigDecimal.ONE), photos(1)))
                .isInstanceOf(ValidationException.class);
        assertCode(() -> service.publish(alice, new ListingRequest(ListingType.GIFT, "Livres", "x", "Livres", null), photos(7)), "INVALID_FILE");
        ListingDto gift = service.publish(alice, new ListingRequest(ListingType.GIFT, "Livres", "Lot de romans", "Livres", null), photos(3));
        assertThat(gift.photos()).hasSize(3);
        assertThat(service.photo(gift.id(), gift.photos().get(0).id()).contentType()).isEqualTo("image/png");
    }

    @Test
    void tenActiveListingsMaxAndExpiry() {
        for (int i = 0; i < 10; i++) {
            sell("Objet " + i);
        }
        assertCode(() -> sell("Onzième"), "TOO_MANY_LISTINGS");
        ListingDto first = service.mine(alice).stream().map(s -> service.get(alice, s.id())).findFirst().orElseThrow();
        jdbc.update("update classifieds set expires_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(1))), first.id());
        assertThat(service.get(alice, first.id()).status()).isEqualTo(ListingStatus.EXPIRED);
        assertThat(service.search(null, null, null, null, null, 0, 50).content()).hasSize(9);
        sell("Remplace l'expirée");
    }

    @Test
    void messagingRules() {
        ListingDto l = sell("Vélo");
        assertCode(() -> service.message(alice, l.id(), new MessageRequest("Coucou", null)), "OWN_LISTING");
        var conv = service.message(bob, l.id(), new MessageRequest("Toujours dispo ?", null));
        assertThat(conv.otherUserId()).isEqualTo(alice.id());
        var reply = service.message(alice, l.id(), new MessageRequest("Oui !", bob.id()));
        assertThat(reply.messages()).hasSize(2);
        assertThat(service.conversations(bob)).singleElement().extracting(c -> c.messages().size()).isEqualTo(2);
        assertThat(service.conversations(admin)).isEmpty();
        service.changeStatus(alice, l.id(), ListingStatus.CLOSED);
        assertCode(() -> service.message(admin, l.id(), new MessageRequest("Trop tard ?", null)), "LISTING_UNAVAILABLE");
    }

    @Test
    void threeReportsHideTheListingUntilAdminRestores() {
        ListingDto l = sell("Objet douteux");
        AuthUser carl = auth(user("Carl"));
        service.report(bob, l.id(), "Arnaque");
        assertCode(() -> service.report(bob, l.id(), "Encore"), "ALREADY_REPORTED");
        service.report(carl, l.id(), "Interdit");
        service.report(admin, l.id(), "Vérification");
        assertCode(() -> service.get(bob, l.id()), "NOT_FOUND");
        assertThat(service.get(alice, l.id()).hidden()).isTrue();
        service.restore(admin, l.id());
        assertThat(service.get(bob, l.id()).hidden()).isFalse();
    }

    @Test
    void multipartPublication() throws Exception {
        var data = new MockMultipartFile("data", "", "application/json",
                toJson(Map.of("type", "SERVICE", "title", "Cours de guitare", "description", "Débutants", "category", "Musique")).getBytes());
        mvc.perform(multipart("/api/classifieds").file(data)
                        .file(new MockMultipartFile("photos", "g.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", bearer(aliceUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.price").doesNotExist());
    }
}
