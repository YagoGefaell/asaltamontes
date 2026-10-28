package io.github.yagogefaell.asaltamontes.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public UserSelfDto getMe(@AuthenticationPrincipal UserAccount currentUser) {
        return userService.getMe(currentUser.getId());
    }

    @PatchMapping("/me")
    public UserSelfDto editMe(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody UserUpdateDto dto
    ) {
        return userService.editProfile(currentUser.getId(), dto);
    }

    @GetMapping("/search")
    public Page<UserSearchDto> getUsers(
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "username") Pageable pageable
    ) {
        if (query == null || query.isBlank()) {
            return userService.getAllUsers(currentUser.getId(), pageable);
        }

        return userService.searchUsersByUsername(query, currentUser.getId(), pageable);
    }

    /** Users to follow: followed by people I follow or in my communities; never ones I follow or blocked. */
    @GetMapping("/suggestions")
    public Page<UserSearchDto> getSuggestions(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return userService.getSuggestions(currentUser.getId(), pageable);
    }

    @GetMapping("{username}")
    public UserProfileDto getUserByUsername(
        @AuthenticationPrincipal UserAccount currentUser,
        @PathVariable String username
    ) {
        return userService.getUserByUsername(username, currentUser.getId());
    }
}

