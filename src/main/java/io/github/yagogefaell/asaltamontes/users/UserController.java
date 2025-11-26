package io.github.yagogefaell.asaltamontes.users;

import io.github.yagogefaell.asaltamontes.users.settings.dto.UserResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> users() {
        return userService.findAllUsers()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole().name()
                ))
                .collect(Collectors.toList());
    }

    @PostMapping
    public UserResponse createUser(@RequestBody User user) {
        User savedUser = userService.saveUser(user);
        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );
    }
}
