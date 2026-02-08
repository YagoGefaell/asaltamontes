package io.github.yagogefaell.asaltamontes.user.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.dto.ChangeProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserMe;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearch;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserMe getMe(Authentication authentication) {
        return userService.getMe(((UserAccount) authentication.getPrincipal()).getId());
    }

    @PatchMapping("/me")
    public UserMe editMe(
            Authentication authentication,
            @Valid @RequestBody ChangeProfile dto
    ) {
        return userService.editProfile(((UserAccount) authentication.getPrincipal()).getId(), dto);
    }

    @GetMapping("/search")
    public List<UserSearch> getUsers(Authentication authentication, @RequestParam("query") String query) {
        Long excludeId = ((UserAccount) authentication.getPrincipal()).getId();
        return userService.searchUsersByUsername(query, excludeId);
    }
}

