package io.github.yagogefaell.asaltamontes.profiles;

import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.profiles.dto.CreateProfileRequest;
import io.github.yagogefaell.asaltamontes.users.User;
import io.github.yagogefaell.asaltamontes.users.UserRepository;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository, UserRepository userRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
    }

    @Override
    public UserProfile createProfile(CreateProfileRequest request) {
        if (userProfileRepository.existsByUserId(request.userId())) {
            throw new IllegalArgumentException("Profile already exists for user ID: " + request.userId());
        }
        User user = userRepository.findById(request.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPublicProfile(false);

        return userProfileRepository.save(profile);
    }

    @Override
    public Optional<UserProfile> getProfile(long userId) {
        return userProfileRepository.findByUserId(userId);
    }

    @Override
    public UserProfile updateProfile(long userId, UserProfile updatedProfile) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteProfile(long userId) {
        if (!userProfileRepository.existsById(userId)) {
            throw new IllegalArgumentException("No existe perfil para eliminar con ID: " + userId);
        }
        userProfileRepository.deleteById(userId);
    }
}