package io.github.yagogefaell.asaltamontes.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.common.exceptions.AlreadyFollowingException;
import io.github.yagogefaell.asaltamontes.common.exceptions.SelfFollowException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserFollow;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;

    @Transactional
    public void followUser(Long followerId, String usernameToFollow) {
        UserAccount following = getUserToFollowOrThrow(followerId, usernameToFollow);

        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, following.getId())) {
            throw new AlreadyFollowingException(usernameToFollow);
        }

        try {
            // Flush inside the try so a concurrent duplicate fails here (unique constraint) and not at commit
            userFollowRepository.saveAndFlush(
                new UserFollow(
                    userAccountRepository.getReferenceById(followerId),
                    following
                )
            );
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyFollowingException(usernameToFollow);
        }

        userProfileRepository.incrementFollowing(followerId);
        userProfileRepository.incrementFollowers(following.getId());
    }

    @Transactional
    public void unfollowUser(Long followerId, String usernameToUnfollow) {
        Long followingId = userAccountRepository.findByUsernameIgnoreCase(usernameToUnfollow)
                .orElseThrow(() -> new UserNotFoundException(usernameToUnfollow))
                .getId();

        userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);

        userProfileRepository.decrementFollowing(followerId);
        userProfileRepository.decrementFollowers(followingId);
    }

    private UserAccount getUserToFollowOrThrow(Long followerId, String username) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (followerId.equals(user.getId())) {
            throw new SelfFollowException();
        }

        return user;
    }

    @Transactional(readOnly = true)
    public Page<UserSearchDto> getUsersFollowedBy(String username, Long currentUserId, Pageable pageable) {
        return userFollowRepository.findFollowingByUsername(username, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserSearchDto> getUsersFollowing(String username, Long currentUserId, Pageable pageable) {
        return userFollowRepository.findFollowersByUsername(username, currentUserId, pageable);
    }
}
