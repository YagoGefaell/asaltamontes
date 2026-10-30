package io.github.yagogefaell.asaltamontes.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;

/**
 * Who may see what a profile holds besides its header (T23a, D-025): followers and following lists, the events she
 * organizes, her communities and her posts. The header (name, picture, bio, counters, the Follow button) is always
 * visible. A private profile is visible to herself and to her accepted followers; blocks (D-022) are checked
 * separately by each caller. Post queries apply the same rule in JPQL ({@code PostQueryRepository.PROFILE_VISIBLE}).
 */
@Service
@RequiredArgsConstructor
public class ProfileAccess {

    private final UserProfileRepository profiles;
    private final UserFollowRepository follows;

    @Transactional(readOnly = true)
    public boolean canViewContent(Long viewerId, Long ownerId) {
        if (viewerId.equals(ownerId)) {
            return true;
        }
        // A profile without a row cannot be shown
        if (profiles.findIsPublicProfile(ownerId).orElse(false)) {
            return true;
        }
        return follows.existsByFollowerIdAndFollowingId(viewerId, ownerId);
    }
}
