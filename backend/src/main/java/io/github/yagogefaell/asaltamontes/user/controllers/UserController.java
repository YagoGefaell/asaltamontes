package io.github.yagogefaell.asaltamontes.user.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.user.profiles.dto.ChangeProfileDTO;
import io.github.yagogefaell.asaltamontes.user.profiles.dto.UserMeDTO;
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
    public UserMeDTO getMe(Authentication authentication) {
        return userService.getMe(((UserAccount) authentication.getPrincipal()).getId());
    }

    @PatchMapping("/me")
    public UserMeDTO editMe(
            Authentication authentication,
            @Valid @RequestBody ChangeProfileDTO dto
    ) {
        return userService.editProfile(dto);
    }

    @GetMapping("/")
    public String getUsers(@RequestParam String param) {
        return new String();
    }
}

