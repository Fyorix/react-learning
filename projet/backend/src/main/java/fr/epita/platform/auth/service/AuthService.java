package fr.epita.platform.auth.service;

import fr.epita.platform.auth.dto.AuthDtos.TokenResponse;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.config.JwtService;
import fr.epita.platform.user.dto.UserDtos.UserDto;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import fr.epita.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = users.findByEmail(UserService.normalize(email))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Email ou mot de passe incorrect"));
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Ce compte est désactivé");
        }
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        UUID id = jwtService.parseRefresh(refreshToken);
        User user = users.findById(id)
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Jeton invalide ou expiré"));
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID id) {
        return users.findById(id).map(UserDto::of).orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    private TokenResponse tokens(User user) {
        return new TokenResponse(jwtService.accessToken(user), jwtService.refreshToken(user),
                jwtService.accessTtlSeconds(), UserDto.of(user));
    }
}
