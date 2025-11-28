package io.github.yagogefaell.asaltamontes.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.github.yagogefaell.asaltamontes.users.dto.UpdateUserRequest;

public interface UserService {

    User registerUser(String username, String email, String password);

    User findById(Long id);

    User findByEmail(String email);

    Page<User> findAll(Pageable pageable);

    User updateUser(Long id, UpdateUserRequest request);

    void changePassword(Long id, String oldPassword, String newPassword);

    void deleteUser(Long id);

    Page<User> searchUsers(String usernameQuery, Pageable pageable);
}