package fr.epita.platform.user;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règle critique §4.4 : il reste toujours au moins un admin actif. */
class UserServiceTest extends IntegrationTest {

    @Autowired
    UserService userService;

    @Test
    void deletingTheLastAdminIsRefused() {
        User admin = admin("Seul Admin");
        user("Simple User");

        assertLastAdminRequired(() -> userService.delete(admin.getId()));
        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    @Test
    void demotingTheLastAdminIsRefused() {
        User admin = admin("Seul Admin");

        assertLastAdminRequired(() -> userService.update(admin.getId(),
                new UpdateUserRequest(admin.getEmail(), admin.getDisplayName(), Role.USER, true)));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void disablingTheLastAdminIsRefused() {
        User admin = admin("Seul Admin");

        assertLastAdminRequired(() -> userService.update(admin.getId(),
                new UpdateUserRequest(admin.getEmail(), admin.getDisplayName(), Role.ADMIN, false)));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void aDisabledAdminDoesNotCountAsActiveAdmin() {
        User active = admin("Admin Actif");
        User disabled = admin("Admin Inactif");
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);

        assertLastAdminRequired(() -> userService.delete(active.getId()));
    }

    @Test
    void sameActionsAreAllowedWhenAnotherAdminRemains() {
        User a = admin("Admin A");
        User b = admin("Admin B");
        User c = admin("Admin C");
        admin("Admin D");

        userService.update(a.getId(), new UpdateUserRequest(a.getEmail(), a.getDisplayName(), Role.USER, true));
        userService.update(b.getId(), new UpdateUserRequest(b.getEmail(), b.getDisplayName(), Role.ADMIN, false));
        userService.delete(c.getId());

        assertThat(userRepository.findById(a.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(userRepository.findById(b.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(userRepository.existsById(c.getId())).isFalse();
        assertThat(userRepository.lockActiveAdminsCount()).isEqualTo(1);
    }

    @Test
    void anAdminCannotDeleteOrDemoteHimselfWhenLast() {
        User admin = admin("Seul Admin");

        assertLastAdminRequired(() -> userService.delete(admin.getId()));
        assertLastAdminRequired(() -> userService.update(admin.getId(),
                new UpdateUserRequest(admin.getEmail(), admin.getDisplayName(), Role.USER, true)));
    }

    @Test
    void twoConcurrentDeletionsOfTheLastTwoAdminsCannotBothSucceed() throws Exception {
        for (int round = 0; round < 5; round++) {
            cleanDatabaseForRound();
            User a = admin("Admin A" + round);
            User b = admin("Admin B" + round);

            List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<?>> futures = new ArrayList<>();
            for (UUID id : List.of(a.getId(), b.getId())) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        userService.delete(id);
                        outcomes.add("OK");
                    } catch (ApiException e) {
                        outcomes.add(e.getCode());
                    } catch (Exception e) {
                        outcomes.add(e);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            pool.shutdown();

            assertThat(outcomes).containsExactlyInAnyOrder("OK", UserService.LAST_ADMIN_REQUIRED);
            assertThat(userRepository.lockActiveAdminsCount()).isEqualTo(1);
        }
    }

    private void cleanDatabaseForRound() {
        jdbc.execute("TRUNCATE users CASCADE");
    }

    private void assertLastAdminRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(UserService.LAST_ADMIN_REQUIRED);
    }
}
