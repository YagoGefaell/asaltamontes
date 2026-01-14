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
import io.github.yagogefaell.asaltamontes.profiles.dto.PublicProfileResponse;
import io.github.yagogefaell.asaltamontes.profiles.dto.PrivateProfileResponse;
import io.github.yagogefaell.asaltamontes.security.user.CustomUserDetails;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

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
    public ResponseEntity<?> getProfile(@PathVariable long userId, Authentication authentication) {
        Optional<UserProfile> profileOpt = userProfileService.getProfile(userId);

        if (profileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            if (userDetails.getUser().getId() == userId || userDetails.getUser().getRole().name().equals("ADMIN")) {
                UserProfile profile = profileOpt.get();
                return ResponseEntity.ok(new PrivateProfileResponse(profile.getUserId(), profile.getFirstName(), profile.getLastName(), profile.getBio(), profile.getLocation(), profile.getProfilePictureUrl(), profile.getInstagramProfileUrl(), profile.getStravaProfileUrl()));
            }
        }

        UserProfile profile = profileOpt.get();

        if (profile.isPublicProfile()) {
            return ResponseEntity.ok(new PrivateProfileResponse(profile.getUserId(), profile.getFirstName(), profile.getLastName(), profile.getBio(), profile.getLocation(), profile.getProfilePictureUrl(), profile.getInstagramProfileUrl(), profile.getStravaProfileUrl()));
        } 
        
        return ResponseEntity.ok(new PublicProfileResponse(profile.getUserId(), profile.getBio(), profile.getLocation(), profile.getProfilePictureUrl()));
    }

    @PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable long userId) {
        userProfileService.deleteProfile(userId);
        return ResponseEntity.noContent().build();
    }
}