package io.github.yagogefaell.asaltamontes.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.dto.UserUpdateDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSelfDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserProfileDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
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
    public UserSelfDto getMe(Authentication authentication) {
        return userService.getMe(((UserAccount) authentication.getPrincipal()).getId());
    }

    @PatchMapping("/me")
    public UserSelfDto editMe(
            Authentication authentication,
            @Valid @RequestBody UserUpdateDto dto
    ) {
        return userService.editProfile(((UserAccount) authentication.getPrincipal()).getId(), dto);
    }

    @GetMapping("/search")
    public Page<UserSearchDto> getUsers(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "username") Pageable pageable
    ) {
        Long userId = ((UserAccount) authentication.getPrincipal()).getId();

        if (query == null || query.isBlank()) {
            return userService.getAllUsers(userId, pageable);
        }

        return userService.searchUsersByUsername(query, userId, pageable);
    }

    @GetMapping("{username}")
    public UserProfileDto getUserByUsername(Authentication authentication, @PathVariable String username) {
        Long requesterId = ((UserAccount) authentication.getPrincipal()).getId();
        return userService.getUserByUsername(username, requesterId);
    }
}

