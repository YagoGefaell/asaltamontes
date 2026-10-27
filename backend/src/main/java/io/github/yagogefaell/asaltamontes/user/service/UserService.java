package io.github.yagogefaell.asaltamontes.user.service;

import java.util.UUID;

import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccountRole;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserUpdateDto;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import io.github.yagogefaell.asaltamontes.user.dto.UserSelfDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserProfileDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;
    private final BlockService blockService;
    private final UserValidator validator;
    private final UserProfileUpdater updater;
    private final UserAssembler assembler;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final MediaReferenceService mediaReferences;
    private final MediaCleanupService mediaCleanup;

    @Transactional
    public UserAccount registerUserWithProfile(
            String fullName,
            String username,
            String email,
            String password,
            String confirmPassword
    ) {
        validator.validateNewUser(fullName, username, email, password, confirmPassword);

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserAccountRole.USER);
        user.setIsVerified(false);

        user = userAccountRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(fullName);
        profile.setIsPublicProfile(false);

        userProfileRepository.save(profile);

        return user;
    }

    @Transactional
    public UserAccount findOrCreateGoogleUser(String email, String fullName, String picture, String givenName) {
        return userAccountRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> {
                UserAccount user = new UserAccount();
                user.setEmail(email);
                user.setUsername(usernameGenerator.generate(givenName != null ? givenName : fullName));
                user.setPasswordHash(null);
                user.setRole(UserAccountRole.USER);
                user.setIsVerified(false);
                
                user = userAccountRepository.save(user);

                UserProfile profile = new UserProfile();
                profile.setUser(user);
                profile.setFullName(fullName);
                profile.setProfilePictureUrl(picture);
                profile.setIsPublicProfile(false);

                userProfileRepository.save(profile);
                
                return user;
            });
    }

    @Transactional
    public UserSelfDto editProfile(long id, UserUpdateDto dto) {

        validator.validateProfileChange(id, dto);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        updater.applyChanges(user, profile, dto);
        linkAvatar(profile, dto);

        return assembler.toUserMe(user, profile);
    }

    /**
     * Sets or removes the profile picture when the request carries one. The previous picture is deleted when
     * nothing else references it; an unchanged legacy external URL (e.g. the Google one) is kept as is.
     */
    private void linkAvatar(UserProfile profile, UserUpdateDto dto) {
        if (dto.avatarMediaId() == null && dto.profilePictureUrl() == null) {
            return;
        }
        UUID previous = profile.getAvatarMediaId();
        String legacyExternal = previous == null ? profile.getProfilePictureUrl() : null;
        MediaReferenceService.Linked avatar = mediaReferences.link(dto.avatarMediaId(), dto.profilePictureUrl(),
                MediaPurpose.AVATAR, profile.getUserId(), legacyExternal);
        profile.setAvatarMediaId(avatar.mediaId());
        profile.setProfilePictureUrl(avatar.url());
        if (previous != null && !previous.equals(avatar.mediaId())) {
            mediaCleanup.deleteIfUnreferenced(previous, null);
        }
    }

    @Transactional(readOnly = true)
    public UserSelfDto getMe(long id) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return assembler.toUserMe(user, profile);
    }

    @Transactional
    public void deleteUser(long id) {
        if (!userAccountRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        // Images first (rows and, after the commit, files): fk_media_owner would block the account otherwise
        mediaCleanup.deleteAllOwnedBy(id);
        userProfileRepository.deleteById(id);
        userAccountRepository.deleteById(id);
    }

    public Page<UserSearchDto> searchUsersByUsername(String query, Long excludeId, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }

        return userAccountRepository.searchUsersByUsername(query, excludeId, pageable);
    }

    public UserAccount loadUserById(long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /** The accounts whose lower-cased username is in the collection; the ones that do not exist are simply missing. */
    public java.util.List<UserAccount> loadUsersByUsernames(java.util.Collection<String> usernames) {
        return userAccountRepository.findByLowerUsernameIn(usernames);
    }

    public UserProfileDto getUserByUsername(String username, Long requesterId) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException(username));

        // Whoever blocked the requester does not exist for her (404, like an unknown username)
        if (blockService.hasBlocked(user.getId(), requesterId)) {
            throw new UserNotFoundException(username);
        }

        boolean isFollowing = userFollowRepository.existsByFollowerIdAndFollowingId(requesterId, user.getId());
        boolean isBlockedByMe = blockService.hasBlocked(requesterId, user.getId());

        return assembler.toUserProfile(user, profile, isFollowing, isBlockedByMe);
    }

    public Page<UserSearchDto> getAllUsers(Long userId, Pageable pageable) {
        return userAccountRepository.searchAllUsers(userId, pageable);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}
