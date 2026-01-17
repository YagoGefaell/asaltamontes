package io.github.yagogefaell.asaltamontes.user.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.user.profiles.dto.ChangeProfileDTO;
import io.github.yagogefaell.asaltamontes.user.profiles.dto.UserMeDTO;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserMeDTO> getMe(Authentication authentication) {
        return ResponseEntity.ok(
                userService.getMe(authentication.getName())
        );
    }

    @PatchMapping("/me")
    public ResponseEntity<UserMeDTO> editMe(
            Authentication authentication,
            @RequestBody ChangeProfileDTO dto
    ) {
        return ResponseEntity.ok(
                userService.editProfile(authentication.getName(), dto)
        );
    }
}

