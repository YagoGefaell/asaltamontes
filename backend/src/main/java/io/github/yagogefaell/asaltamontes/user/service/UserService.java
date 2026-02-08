package io.github.yagogefaell.asaltamontes.user.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccountRole;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.ChangeProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserMe;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearch;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    // ------------------ REGISTRO ------------------
    @Transactional
    public UserAccount registerUserWithProfile(
            String fullName,
            String username,
            String email,
            String password,
            String confirmPassword
    ) {
        validateNewUser(fullName, username, email, password, confirmPassword);

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserAccountRole.USER);
        user.setIsVerified(false);

        user = userAccountRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(fullName);
        profile.setIsPublicProfile(false);

        userProfileRepository.save(profile);

        return user;
    }

    private void validateNewUser(String fullName, String username, String email, String password, String confirmPassword) {
        Map<String, String> errors = new HashMap<>();

        if (fullName == null || fullName.isBlank()) {
            errors.put("fullName", "Nombre completo requerido");
        } else if (fullName.length() < 3 || fullName.length() > 50) {
            errors.put("fullName", "Debe tener entre 3 y 50 caracteres");
        } else if (!fullName.matches("^[a-zA-ZÀ-ÿ\\s]+$")) {
            errors.put("fullName", "No se permiten números ni caracteres especiales");
        }

        if (username == null || username.isBlank()) {
            errors.put("username", "Username requerido");
        } else if (username.length() < 3 || username.length() > 50) {
            errors.put("username", "Debe tener entre 3 y 50 caracteres");
        } else if (!username.matches("^[a-zA-Z0-9._]+$")) {
            errors.put("username", "No se permiten espacios ni caracteres especiales");
        } else if (userAccountRepository.existsByUsername(username)) {
            errors.put("username", "Username ya registrado");
        }

        if (email == null || email.isBlank()) {
            errors.put("email", "Email requerido");
        } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errors.put("email", "Formato de email inválido");
        } else if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            errors.put("email", "Email ya registrado");
        }

        if (password == null || password.isBlank()) {
            errors.put("password", "Contraseña requerida");
        } else if (password.length() < 6) {
            errors.put("password", "Contraseña demasiado corta");
        }

        if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "Las contraseñas no coinciden");
        }

        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }
    }

    // ------------------ EDICIÓN DE PERFIL ------------------
    @Transactional
    public UserMe editProfile(Long userId, ChangeProfile dto) {
        validateChangeProfile(userId, dto);

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (dto.fullName() != null) {
            profile.setFullName(dto.fullName().trim());
        }

        if (dto.username() != null) {
            user.setUsername(dto.username().trim());
        }

        if (dto.bio() != null) {
            profile.setBio(dto.bio().trim());
        }

        if (dto.city() != null) {
            profile.setCity(dto.city().trim());
        }

        if (dto.profilePictureUrl() != null) {
            profile.setProfilePictureUrl(dto.profilePictureUrl().trim());
        }

        if (dto.isPublicProfile() != null) {
            profile.setIsPublicProfile(dto.isPublicProfile());
        }

        return assembleUserMeDTO(user, profile);
    }

    private void validateChangeProfile(Long userId, ChangeProfile dto) {
        Map<String, String> errors = new HashMap<>();

        // USERNAME
        if (dto.username() != null) {
            String username = dto.username().trim();

            if (username == null || username.isBlank()) {
                errors.put("username", "Username requerido");
            } else if (username.length() < 3 || username.length() > 50) {
                errors.put("username", "Debe tener entre 3 y 50 caracteres");
            } else if (!username.matches("^[a-zA-Z0-9._]+$")) {
                errors.put("username", "No se permiten espacios ni caracteres especiales");
            } else if (userAccountRepository.existsByUsernameExceptId(username, userId)) {
                errors.put("username", "Username ya registrado");
            }
        }

        // EMAIL
        if (dto.email() != null) {
            String email = dto.email().trim();

            if (email == null || email.isBlank()) {
                errors.put("email", "Email requerido");
            } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.put("email", "Formato de email inválido");
            } else if (userAccountRepository.existsByEmailIgnoreCase(email)) {
                errors.put("email", "Email ya registrado");
            }
        }

        // FULL NAME
        if (dto.fullName() != null) {
            String fullName = dto.fullName().trim();
            if (fullName == null || fullName.isBlank()) {
                errors.put("fullName", "Nombre completo requerido");
            } else if (fullName.length() < 3 || fullName.length() > 50) {
                errors.put("fullName", "Debe tener entre 3 y 50 caracteres");
            } else if (!fullName.matches("^[a-zA-ZÀ-ÿ\\s]+$")) {
                errors.put("fullName", "No se permiten números ni caracteres especiales");
            }
        }

        // BIO
        if (dto.bio() != null) {
            if (dto.bio().length() > 500) {
                errors.put("bio", "Máximo 500 caracteres");
            }
        }

        // CITY
        if (dto.city() != null) {
            String city = dto.city().trim();
            if (city.length() < 2 || city.length() > 50) {
                errors.put("city", "Debe tener entre 2 y 50 caracteres");
            }
        }

        // PROFILE PICTURE URL
        if (dto.profilePictureUrl() != null) {
            String url = dto.profilePictureUrl().trim();
            if (!url.matches("^(http|https)://.*$")) {
                errors.put("profilePictureUrl", "Debe ser una URL válida con http o https");
            }
        }

        // THROW IF ERRORS
        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }
    }


    // ------------------ DELETE ------------------
    @Transactional
    public void deleteUser(long id) {

        if (!userAccountRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userProfileRepository.deleteById(id);
        userAccountRepository.deleteById(id);
    }

    // ------------------ DTO ------------------
    private UserMe assembleUserMeDTO(UserAccount user, UserProfile profile) {
        return new UserMe(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsVerified(),
                profile.getFullName() != null ? profile.getFullName() : "",
                profile.getBio() != null ? profile.getBio() : "",
                profile.getCity() != null ? profile.getCity() : "",
                profile.getProfilePictureUrl() != null ? profile.getProfilePictureUrl() : "",
                profile.getRunningLevel() != null ? profile.getRunningLevel().name() : "BEGINNER",
                profile.getBirthDate(),
                profile.getPhoneNumber(),
                profile.getIsPublicProfile(),
                0,
                0
        );
    }

    @Transactional(readOnly = true)
    public UserMe getMe(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return assembleUserMeDTO(user, profile);
    }
    
    public List<UserSearch> searchUsersByUsername(String query, Long excludeId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return userAccountRepository.searchUsersByUsername(query, excludeId)
            .stream()
            .map(u -> new UserSearch(
                u.getUsername()
            ))
            .toList();
    }

    public UserAccount loadUserById(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}
