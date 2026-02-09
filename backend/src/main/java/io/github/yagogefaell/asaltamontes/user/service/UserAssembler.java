package io.github.yagogefaell.asaltamontes.user.service;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserMe;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearch;

@Component
public class UserAssembler {
    public UserMe toUserMe(UserAccount user, UserProfile profile) {
        return new UserMe(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsVerified(),
                safe(profile.getFullName()),
                safe(profile.getBio()),
                safe(profile.getCity()),
                safe(profile.getProfilePictureUrl()),
                profile.getRunningLevel() != null ? profile.getRunningLevel().name() : "BEGINNER",
                profile.getBirthDate(),
                profile.getPhoneNumber(),
                profile.getIsPublicProfile(),
                0,
                0
        );
    }

    public UserSearch toUserSearch(UserAccount user) {
        return new UserSearch(user.getUsername());
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
