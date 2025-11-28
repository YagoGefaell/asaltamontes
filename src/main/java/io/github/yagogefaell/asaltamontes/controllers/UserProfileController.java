package io.github.yagogefaell.asaltamontes.controllers;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.profiles.UserProfile;
import io.github.yagogefaell.asaltamontes.profiles.UserProfileService;
import io.github.yagogefaell.asaltamontes.profiles.dto.CreateProfileRequest;

@RestController
@RequestMapping("/api/profiles")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PostMapping
    public ResponseEntity<UserProfile> createProfile(@RequestBody CreateProfileRequest request) {
    UserProfile profile = userProfileService.createProfile(request);
    return ResponseEntity.ok(profile);
}

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfile> getProfile(@PathVariable long userId) {
        Optional<UserProfile> profileOpt = userProfileService.getProfile(userId);
        return profileOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable long userId) {
        userProfileService.deleteProfile(userId);
        return ResponseEntity.noContent().build();
    }
}