package io.github.yagogefaell.asaltamontes.user.service;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserSelfDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserProfileDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;

@Component
public class UserAssembler {
    public UserSelfDto toUserMe(UserAccount user, UserProfile profile) {
        return new UserSelfDto(
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
                profile.getFollowersCount(),
                profile.getFollowingCount()
        );
    }

    public UserProfileDto toUserProfile(UserAccount user, UserProfile profile, boolean isFollowing) {
        return new UserProfileDto(
                user.getUsername(),
                user.getIsVerified(),
                safe(profile.getFullName()),
                safe(profile.getBio()),
                safe(profile.getCity()),
                safe(profile.getProfilePictureUrl()),
                profile.getRunningLevel() != null ? profile.getRunningLevel().name() : "BEGINNER",
                profile.getIsPublicProfile(),
                isFollowing,
                profile.getFollowersCount(),
                profile.getFollowingCount()
        );
    }

    public UserSearchDto toUserSearch(UserAccount user, UserProfile profile) {
        return new UserSearchDto(
                user.getUsername(),
                safe(profile.getFullName()),
                safe(profile.getProfilePictureUrl())
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
