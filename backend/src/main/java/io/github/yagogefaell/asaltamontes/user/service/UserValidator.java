package io.github.yagogefaell.asaltamontes.user.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.SelfFollowException;
import io.github.yagogefaell.asaltamontes.user.dto.UserUpdateDto;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserAccountRepository userAccountRepository;
    private final UserFollowRepository userFollowRepository;

    // -----------------------------
    // VALIDACIÓN DE REGISTRO
    // -----------------------------
    public void validateNewUser(
            String fullName,
            String username,
            String email,
            String password,
            String confirmPassword
    ) {
        Map<String, String> errors = new HashMap<>();

        validateFullName(fullName, errors);
        validateUsername(username, null, errors);
        validateEmail(email, null, errors);
        validatePassword(password, confirmPassword, errors);

        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }
    }

    // -----------------------------
    // VALIDACIÓN DE EDICIÓN DE PERFIL
    // -----------------------------
    public void validateProfileChange(Long userId, UserUpdateDto dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.username() != null) {
            validateUsername(dto.username().trim(), userId, errors);
        }

        if (dto.email() != null) {
            validateEmail(dto.email().trim(), userId, errors);
        }

        if (dto.fullName() != null) {
            validateFullName(dto.fullName().trim(), errors);
        }

        if (dto.bio() != null && dto.bio().length() > 500) {
            errors.put("bio", "Máximo 500 caracteres");
        }

        if (dto.city() != null) {
            String city = dto.city().trim();
            if (city.length() < 2 || city.length() > 50) {
                errors.put("city", "Debe tener entre 2 y 50 caracteres");
            }
        }

        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }
    }

    // -----------------------------
    // VALIDACIÓN INICIO DE SESIÓN
    // -----------------------------
    public void validateLogin(String username, String password) {
        Map<String, String> errors = new HashMap<>();

        if (username == null || username.isBlank()) {
            errors.put("username", "Username requerido");
        }

        if (password == null || password.isBlank()) {
            errors.put("password", "Contraseña requerida");
        }

        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }
    }   

    // -----------------------------
    // VALIDACIONES INDIVIDUALES
    // -----------------------------
    private void validateFullName(String fullName, Map<String, String> errors) {
        if (fullName == null || fullName.isBlank()) {
            errors.put("fullName", "Nombre completo requerido");
        } else if (fullName.length() < UserFieldRules.FULL_NAME_MIN || fullName.length() > UserFieldRules.FULL_NAME_MAX) {
            errors.put("fullName", UserFieldRules.FULL_NAME_SIZE_MESSAGE);
        } else if (!fullName.matches(UserFieldRules.FULL_NAME_PATTERN)) {
            errors.put("fullName", UserFieldRules.FULL_NAME_PATTERN_MESSAGE);
        }
    }

    private void validateUsername(String username, Long excludeId, Map<String, String> errors) {
        if (username == null || username.isBlank()) {
            errors.put("username", "Username requerido");
        } else if (username.length() < UserFieldRules.USERNAME_MIN || username.length() > UserFieldRules.USERNAME_MAX) {
            errors.put("username", UserFieldRules.USERNAME_SIZE_MESSAGE);
        } else if (!username.matches(UserFieldRules.USERNAME_PATTERN)) {
            errors.put("username", UserFieldRules.USERNAME_PATTERN_MESSAGE);
        } else {
            boolean exists = (excludeId == null)
                    ? userAccountRepository.existsByUsernameIgnoreCase(username)
                    : userAccountRepository.existsByUsernameExceptId(username, excludeId);

            if (exists) {
                errors.put("username", "Username ya registrado");
            }
        }
    }

    private void validateEmail(String email, Long excludeId, Map<String, String> errors) {
        if (email == null || email.isBlank()) {
            errors.put("email", "Email requerido");
        } else if (!email.matches(UserFieldRules.EMAIL_PATTERN)) {
            errors.put("email", UserFieldRules.EMAIL_PATTERN_MESSAGE);
        } else {
            boolean exists = (excludeId == null)
                    ? userAccountRepository.existsByEmailIgnoreCase(email)
                    : userAccountRepository.existsByEmailIgnoreCaseExceptId(email, excludeId);

            if (exists) {
                errors.put("email", "Email ya registrado");
            }
        }
    }

    private void validatePassword(String password, String confirmPassword, Map<String, String> errors) {
        if (password == null || password.isBlank()) {
            errors.put("password", "Contraseña requerida");
        } else if (password.length() < UserFieldRules.PASSWORD_MIN || password.length() > UserFieldRules.PASSWORD_MAX) {
            errors.put("password", UserFieldRules.PASSWORD_SIZE_MESSAGE);
        }

        if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "Las contraseñas no coinciden");
        }
    }

    public void validateFollowAction(long followerId, long followingId) {
        if (followerId == followingId) {
            throw new SelfFollowException();
        }

        boolean alreadyFollowing = userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
        if (alreadyFollowing) {
            throw new ConflictException("Ya sigues a este usuario");
        }
    }
}
