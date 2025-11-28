package io.github.yagogefaell.asaltamontes.profiles;

import java.util.Optional;

import io.github.yagogefaell.asaltamontes.profiles.dto.CreateProfileRequest;

public interface UserProfileService {
    public UserProfile createProfile(CreateProfileRequest request);
    public Optional<UserProfile> getProfile(long userId);
    public UserProfile updateProfile(long userId, UserProfile updatedProfile);
    public void deleteProfile(long userId);
}
