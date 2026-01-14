package io.github.yagogefaell.asaltamontes.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.users.User;
import io.github.yagogefaell.asaltamontes.users.UserService;
import io.github.yagogefaell.asaltamontes.users.dto.ChangePasswordRequest;
import io.github.yagogefaell.asaltamontes.users.dto.PublicUserResponse;
import io.github.yagogefaell.asaltamontes.users.dto.UpdateUserRequest;
import io.github.yagogefaell.asaltamontes.users.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        User user = userService.findById(id);

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Page<PublicUserResponse> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return userService.findAll(PageRequest.of(page, size))
                .map(user -> new PublicUserResponse(
                        user.getId(),
                        user.getUsername()
                ));
            
    }

    @GetMapping("/search")
    public Page<PublicUserResponse> search(@RequestParam String q, 
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return userService.searchUsers(q, PageRequest.of(page, size))
                .map(user -> new PublicUserResponse(
                        user.getId(),
                        user.getUsername()
                ));
    }
    
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        
        User user = userService.updateUser(id, request);

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    @PreAuthorize("#id == authentication.principal.id")
    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request.oldPassword(), request.newPassword());
    }

    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

}
