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
                profile.getDmPolicy().name(),
                profile.getFollowersCount(),
                profile.getFollowingCount()
        );
    }

    public UserProfileDto toUserProfile(UserAccount user, UserProfile profile, boolean isFollowing, boolean isFollowRequested,
                                        boolean canViewContent, boolean isBlockedByMe) {
        return new UserProfileDto(
                user.getUsername(),
                user.getIsVerified(),
                safe(profile.getFullName()),
                safe(profile.getBio()),
                // D-025: a private profile she cannot view shows only name, picture, bio and counters
                canViewContent ? safe(profile.getCity()) : "",
                safe(profile.getProfilePictureUrl()),
                !canViewContent ? "" : profile.getRunningLevel() != null ? profile.getRunningLevel().name() : "BEGINNER",
                profile.getIsPublicProfile(),
                isFollowing,
                isFollowRequested,
                canViewContent,
                isBlockedByMe,
                profile.getFollowersCount(),
                profile.getFollowingCount()
        );
    }

    public UserSearchDto toUserSearch(UserAccount user, UserProfile profile, boolean isFollowing) {
        return new UserSearchDto(
                user.getUsername(),
                safe(profile.getFullName()),
                safe(profile.getProfilePictureUrl()),
                isFollowing
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
