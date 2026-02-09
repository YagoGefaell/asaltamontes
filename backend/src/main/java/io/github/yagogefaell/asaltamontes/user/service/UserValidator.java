package io.github.yagogefaell.asaltamontes.user.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.user.dto.ChangeProfile;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserAccountRepository userAccountRepository;

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
    public void validateProfileChange(Long userId, ChangeProfile dto) {
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

        if (dto.profilePictureUrl() != null) {
            String url = dto.profilePictureUrl().trim();
            if (!url.matches("^(http|https)://.*$")) {
                errors.put("profilePictureUrl", "Debe ser una URL válida con http o https");
            }
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
        } else if (fullName.length() < 3 || fullName.length() > 50) {
            errors.put("fullName", "Debe tener entre 3 y 50 caracteres");
        } else if (!fullName.matches("^[a-zA-ZÀ-ÿ\\s]+$")) {
            errors.put("fullName", "No se permiten números ni caracteres especiales");
        }
    }

    private void validateUsername(String username, Long excludeId, Map<String, String> errors) {
        if (username == null || username.isBlank()) {
            errors.put("username", "Username requerido");
        } else if (username.length() < 3 || username.length() > 50) {
            errors.put("username", "Debe tener entre 3 y 50 caracteres");
        } else if (!username.matches("^[a-zA-Z0-9._]+$")) {
            errors.put("username", "No se permiten espacios ni caracteres especiales");
        } else {
            boolean exists = (excludeId == null)
                    ? userAccountRepository.existsByUsername(username)
                    : userAccountRepository.existsByUsernameExceptId(username, excludeId);

            if (exists) {
                errors.put("username", "Username ya registrado");
            }
        }
    }

    private void validateEmail(String email, Long excludeId, Map<String, String> errors) {
        if (email == null || email.isBlank()) {
            errors.put("email", "Email requerido");
        } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errors.put("email", "Formato de email inválido");
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
        } else if (password.length() < 6) {
            errors.put("password", "Contraseña demasiado corta");
        }

        if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "Las contraseñas no coinciden");
        }
    }
}
