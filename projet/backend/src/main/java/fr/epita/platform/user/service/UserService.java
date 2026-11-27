package fr.epita.platform.user.service;

import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.user.dto.UserDtos.CreateUserRequest;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.dto.UserDtos.UserDto;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final String LAST_ADMIN_REQUIRED = "LAST_ADMIN_REQUIRED";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final List<UserDeletionGuard> guards;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by("displayName").ascending());
        return PageResponse.of(users.search(term, pageable), UserDto::of);
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return UserDto.of(find(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Cet email est déjà utilisé");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        user.setRole(req.role());
        user.setEnabled(true);
        return UserDto.of(users.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User user = find(id);
        boolean losesAdmin = user.isActiveAdmin() && (req.role() != Role.ADMIN || !req.enabled());
        if (losesAdmin) {
            ensureAnotherActiveAdmin(user.getId());
        }
        if (user.isEnabled() && !req.enabled()) {
            guards.forEach(g -> g.beforeRemoval(user));
        }
        String email = normalize(req.email());
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Cet email est déjà utilisé");
        }
        user.setEmail(email);
        user.setDisplayName(req.displayName().trim());
        user.setRole(req.role());
        user.setEnabled(req.enabled());
        return UserDto.of(users.saveAndFlush(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = find(id);
        if (user.isActiveAdmin()) {
            ensureAnotherActiveAdmin(user.getId());
        }
        guards.forEach(g -> g.beforeRemoval(user));
        users.delete(user);
        users.flush();
    }

    @Transactional
    public void changePassword(UUID id, String current, String next) {
        User user = find(id);
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Mot de passe actuel incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
    }

    /**
     * Règle §4.4 : il doit rester au moins un ADMIN actif autre que {@code excludedId}.
     * Les lignes admin sont verrouillées (FOR UPDATE) jusqu'à la fin de la transaction :
     * deux suppressions simultanées des deux derniers admins ne peuvent pas réussir toutes les deux.
     */
    private void ensureAnotherActiveAdmin(UUID excludedId) {
        long others = users.lockActiveAdmins().stream().filter(u -> !u.getId().equals(excludedId)).count();
        if (others == 0) {
            throw ApiException.conflict(LAST_ADMIN_REQUIRED,
                    "L'application doit conserver au moins un administrateur actif");
        }
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
