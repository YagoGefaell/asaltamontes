package io.github.yagogefaell.asaltamontes.security.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.security.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;

    @Transactional
    public UserAccount register(RegisterRequest request) {
        return userService.registerUserWithProfile(
                request.fullName(),
                request.username(),
                request.email(),
                request.password(),
                request.confirmPassword()
        );
    }

    public UserAccount loadUserById(String idString) {
        return userService.loadUserById(idString);
    }

    public UserAccount loadUserByUsername(String username) {
        return userService.loadUserByUsername(username);
    }
}
