package io.github.yagogefaell.asaltamontes.user.service;

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
    private final UserValidator validator;
    private final UserProfileUpdater updater;
    private final UserAssembler assembler;
    private final PasswordEncoder passwordEncoder;
    // ------------------ REGISTRO ------------------
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

    // ------------------ EDICIÓN DE PERFIL ------------------
    @Transactional
    public UserSelfDto editProfile(long id, UserUpdateDto dto) {

        validator.validateProfileChange(id, dto);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        updater.applyChanges(user, profile, dto);

        return assembler.toUserMe(user, profile);
    }

    // ------------------ GET ME ------------------
    @Transactional(readOnly = true)
    public UserSelfDto getMe(long id) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return assembler.toUserMe(user, profile);
    }

    // ------------------ DELETE ------------------
    @Transactional
    public void deleteUser(long id) {
        if (!userAccountRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userProfileRepository.deleteById(id);
        userAccountRepository.deleteById(id);
    }

    // ------------------ SEARCH ------------------
    public Page<UserSearchDto> searchUsersByUsername(String query, Long excludeId, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }

        return userAccountRepository.searchUsersByUsername(query, excludeId, pageable);
    }

    // ------------------ LOAD USER ------------------
    public UserAccount loadUserById(long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    // ------------------ GET USER BY USERNAME ------------------
    public UserProfileDto getUserByUsername(String username, Long requesterId) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException(username));

        boolean isFollowing = userFollowRepository.existsByFollowerIdAndFollowingId(requesterId, user.getId());

        return assembler.toUserProfile(user, profile, isFollowing);
    }

    public Page<UserSearchDto> getAllUsers(Long excludeId, Pageable pageable) {
        return userAccountRepository.searchAllUsers(excludeId, pageable);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}
