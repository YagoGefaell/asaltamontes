package io.github.yagogefaell.asaltamontes.users;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.github.yagogefaell.asaltamontes.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.users.dto.UpdateUserRequest;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User registerUser(String username, String email, String password) {
        Map<String, String> errors = new HashMap<>();

        // Validar username
        if (username == null || username.isBlank()) {
            errors.put("username", "Username requerido");
        } else if (username.length() < 3 || username.length() > 50) {
            errors.put("username", "Debe tener entre 3 y 50 caracteres");
        } else if (!username.matches("^[a-zA-Z0-9._]+$")) {
            errors.put("username", "No se permiten espacios ni caracteres especiales");
        }

        // Validar email
        if (email == null || email.isBlank()) {
            errors.put("email", "Email requerido");
        } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errors.put("email", "Formato de email inválido");
        }

        // Validar password
        if (password == null || password.isBlank()) {
            errors.put("password", "Contraseña requerida");
        } else if (password.length() < 6) {
            errors.put("password", "Contraseña demasiado corta");
        } else if (password.contains(" ")) {
            errors.put("password", "No se permiten espacios");
        }

        // Validar duplicados
        if (userRepository.existsByUsername(username)) {
            errors.put("username", "Username ya registrado");
        }

        if (userRepository.existsByEmail(email)) {
            errors.put("email", "Email ya registrado");
        }

        // Lanzar excepción si hay errores
        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }

        // Crear usuario
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setIsVerified(false);

        return userRepository.save(user);
    }

    @Override
    public User findById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public User updateUser(long id, UpdateUserRequest request) {
        User user = findById(id);

        if (request.email() != null &&
            !request.email().equals(user.getEmail()) &&
            userRepository.existsByEmail(request.email())) {

            throw new MultipleFieldConflictException(Map.of("email", "Email already registered"));
        }

        if (request.username() != null) {
            user.setUsername(request.username());
        }

        if (request.email() != null) {
            user.setEmail(request.email());
        }

        return userRepository.save(user);
    }

    @Override
    public void changePassword(long id, String oldPassword, String newPassword) {
        User user = findById(id);

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public void deleteUser(long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(id);
    }

    @Override
    public Page<User> searchUsers(String usernameQuery, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(usernameQuery, pageable);
    }
}
