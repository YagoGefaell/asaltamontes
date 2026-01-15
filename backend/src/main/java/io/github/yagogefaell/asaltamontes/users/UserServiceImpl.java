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

        if (userRepository.existsByEmail(email)) {
            errors.put("email", "Email already registered");
        }

        if (userRepository.existsByUsername(username)) {
            errors.put("username", "Username already taken");
        }

        if (!errors.isEmpty()) {
            throw new MultipleFieldConflictException(errors);
        }

        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(UserRole.USER);
            user.setIsVerified(false);

            return userRepository.save(user);
        } catch (Exception e) {
            // 500 Internal Server Error → cualquier otro fallo
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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

            throw new ResponseStatusException(HttpStatus.CONFLICT);
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
