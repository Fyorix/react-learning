package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.AssignmentRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonCardDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.ProfileRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillLevel;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.TeamDto;
import fr.epita.platform.apps.directory.service.DirectoryService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryServiceTest extends IntegrationTest {

    @Autowired
    DirectoryService service;

    AuthUser admin;
    AuthUser pika;
    AuthUser sala;
    TeamDto electrik;
    TeamDto feu;
    SkillDto eclair;
    SkillDto flamme;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        pika = auth(user("Pikachu"));
        sala = auth(user("Salamèche"));
        electrik = service.createTeam(admin, "Électrik");
        feu = service.createTeam(admin, "Feu");
        eclair = service.createSkill(admin, new SkillRequest("Éclair", "Attaque"));
        flamme = service.createSkill(admin, new SkillRequest("Flammèche", "Attaque"));
        service.assign(admin, pika.id(), new AssignmentRequest(electrik.id(), null));
        service.assign(admin, sala.id(), new AssignmentRequest(feu.id(), pika.id()));
        service.updateMine(pika, new ProfileRequest("Souris électrique", "Bourg Palette", null, null, List.of(new SkillLevel(eclair.id(), 4))));
        service.updateMine(sala, new ProfileRequest("Lézard de feu", "Bourg Palette", null, null,
                List.of(new SkillLevel(flamme.id(), 3), new SkillLevel(eclair.id(), 1))));
    }

    private List<UUID> ids(List<PersonCardDto> cards) {
        return cards.stream().map(PersonCardDto::userId).toList();
    }

    @Test
    void searchIgnoresAccentsAndCombinesFilters() {
        assertThat(ids(service.search("salameche", null, null, null, null, 0, 24).content())).containsExactly(sala.id());
        assertThat(ids(service.search(null, feu.id(), null, null, null, 0, 24).content())).containsExactly(sala.id());
        assertThat(ids(service.search(null, null, eclair.id(), 3, null, 0, 24).content())).containsExactly(pika.id());
        assertThat(ids(service.search(null, null, eclair.id(), null, null, 0, 24).content())).containsExactlyInAnyOrder(pika.id(), sala.id());
    }

    @Test
    void detailShowsManagerReportsAndSkills() {
        var sheet = service.get(sala.id());
        assertThat(sheet.manager().userId()).isEqualTo(pika.id());
        assertThat(sheet.skills()).extracting("name").containsExactly("Flammèche", "Éclair");
        assertThat(service.get(pika.id()).reports()).extracting("userId").containsExactly(sala.id());
    }

    @Test
    void skillsMustComeFromTheCatalogOnce() {
        assertThatThrownBy(() -> service.updateMine(pika, new ProfileRequest("x", "y", null, null,
                List.of(new SkillLevel(eclair.id(), 2), new SkillLevel(eclair.id(), 3))))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.updateMine(pika, new ProfileRequest("x", "y", null, null,
                List.of(new SkillLevel(UUID.randomUUID(), 2))))).isInstanceOf(ValidationException.class);
    }

    @Test
    void disabledUsersDisappearAndAdminRules() {
        User u = userRepository.findById(sala.id()).orElseThrow();
        u.setEnabled(false);
        userRepository.saveAndFlush(u);
        assertThat(ids(service.search(null, null, null, null, null, 0, 24).content())).doesNotContain(sala.id());
        assertThatThrownBy(() -> service.get(sala.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.assign(pika, pika.id(), new AssignmentRequest(null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.assign(admin, pika.id(), new AssignmentRequest(null, pika.id())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SELF_MANAGER");
        assertThat(service.teams()).filteredOn(t -> t.id().equals(feu.id())).extracting(TeamDto::members).containsExactly(0L);
    }

    @Test
    void photoUploadAcceptsImagesOnly() {
        var png = new MockMultipartFile("file", "pika.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        assertThat(service.updatePhoto(pika, png).hasPhoto()).isTrue();
        assertThat(service.photo(pika.id()).contentType()).isEqualTo("image/png");
        var pdf = new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service.updatePhoto(pika, pdf)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_FILE");
    }
}
