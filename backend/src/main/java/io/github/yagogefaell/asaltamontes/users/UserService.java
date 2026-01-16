package io.github.yagogefaell.asaltamontes.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.github.yagogefaell.asaltamontes.users.dto.UpdateUserRequest;

public interface UserService {

    User registerUser(String username, String email, String password, String confirmPassword);

    User findById(long id);

    User findByEmail(String email);

    Page<User> findAll(Pageable pageable);

    User updateUser(long id, UpdateUserRequest request);

    void changePassword(long id, String oldPassword, String newPassword);

    void deleteUser(long id);
    
    Page<User> searchUsers(String usernameQuery, Pageable pageable);
}