package io.github.yagogefaell.asaltamontes.user.service;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccountRole;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.ChangeProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserMe;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearch;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserValidator validator;
    private final UserProfileUpdater updater;
    private final UserAssembler assembler;

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
        user.setPasswordHash(password);
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
    public UserMe editProfile(long id, ChangeProfile dto) {

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
    public UserMe getMe(long id) {
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
    public List<UserSearch> searchUsersByUsername(String query, Long excludeId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return userAccountRepository.searchUsersByUsername(query, excludeId)
                .stream()
                .map(assembler::toUserSearch)
                .toList();
    }

    // ------------------ LOAD USER ------------------
    public UserAccount loadUserById(long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}
