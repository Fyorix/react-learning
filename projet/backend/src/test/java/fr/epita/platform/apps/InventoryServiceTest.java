package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentRequest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.StatusRequest;
import fr.epita.platform.apps.inventory.entity.EquipmentStatus;
import fr.epita.platform.apps.inventory.entity.EquipmentType;
import fr.epita.platform.apps.inventory.entity.IssueStatus;
import fr.epita.platform.apps.inventory.service.InventoryService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest extends IntegrationTest {

    @Autowired
    InventoryService service;
    @Autowired
    UserService userService;

    private EquipmentDto laptop(String serial) {
        return service.create(new EquipmentRequest(EquipmentType.LAPTOP, "Dell", "Latitude", serial,
                LocalDate.now().minusMonths(6)));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void serialNumberIsUnique() {
        laptop("SN-1");
        assertCode(() -> laptop("sn-1"), "SERIAL_NUMBER_ALREADY_USED");
    }

    @Test
    void onlyAvailableEquipmentCanBeAssignedAndHistoryIsKept() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        EquipmentDto e = laptop("SN-1");
        service.assign(e.id(), alice.id());
        assertCode(() -> service.assign(e.id(), bob.id()), "EQUIPMENT_NOT_AVAILABLE");
        service.release(e.id());
        service.assign(e.id(), bob.id());
        assertThat(service.detail(e.id()).history()).hasSize(2);
        assertThat(service.mine(bob)).hasSize(1);
        assertThat(service.mine(alice)).isEmpty();
    }

    @Test
    void onlyTheHolderReportsAndBrokenEquipmentIsReleased() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        EquipmentDto e = laptop("SN-1");
        service.assign(e.id(), alice.id());
        assertCode(() -> service.report(bob, e.id(), "Écran cassé"), "FORBIDDEN");
        service.report(alice, e.id(), "Écran cassé");
        assertThat(service.adminIssues("open")).hasSize(1);

        EquipmentDto broken = service.changeStatus(e.id(), new StatusRequest(EquipmentStatus.EN_PANNE, null));
        assertThat(broken.holderId()).isNull();
        assertCode(() -> service.assign(e.id(), bob.id()), "EQUIPMENT_NOT_AVAILABLE");

        service.changeStatus(e.id(), new StatusRequest(EquipmentStatus.DISPONIBLE, "Réparé"));
        assertThat(service.adminIssues("open")).isEmpty();
        assertThat(service.adminIssues("resolved").getFirst().status()).isEqualTo(IssueStatus.RESOLVED);
    }

    @Test
    void scrapIsFinal() {
        EquipmentDto e = laptop("SN-1");
        service.changeStatus(e.id(), new StatusRequest(EquipmentStatus.REBUT, null));
        assertCode(() -> service.changeStatus(e.id(), new StatusRequest(EquipmentStatus.DISPONIBLE, null)),
                "EQUIPMENT_SCRAPPED");
    }

    @Test
    void aUserHoldingEquipmentCannotBeDeletedNorDisabled() {
        admin("Admin");
        User alice = user("Alice");
        EquipmentDto e = laptop("SN-1");
        service.assign(e.id(), alice.getId());

        assertCode(() -> userService.delete(alice.getId()), "USER_HOLDS_EQUIPMENT");
        assertCode(() -> userService.update(alice.getId(),
                new UpdateUserRequest(alice.getEmail(), alice.getDisplayName(), Role.USER, false)), "USER_HOLDS_EQUIPMENT");

        service.release(e.id());
        userService.delete(alice.getId());
        assertThat(userRepository.existsById(alice.getId())).isFalse();
    }
}
