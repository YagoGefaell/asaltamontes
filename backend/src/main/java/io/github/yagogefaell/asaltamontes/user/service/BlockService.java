package io.github.yagogefaell.asaltamontes.user.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.DmPolicy;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.dto.BlockedUserDto;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserBlockRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;

/**
 * Blocks between users (T13). A block works in both directions for every interaction (private chats, follows,
 * adding to groups, visibility); the other side is never told. Reusable by later features (T19, T22, T23).
 * Shared groups are not touched: nobody is expelled and the messages stay visible.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    public static final int MAX_PAGE_SIZE = 50;

    /** Deliberately says nothing about who blocked whom or which policy applied. */
    public static final String NOT_ALLOWED_MESSAGE = "No puedes interactuar con esta usuaria";

    private final UserBlockRepository blockRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;
    private final io.github.yagogefaell.asaltamontes.user.repository.FollowRequestRepository followRequestRepository;

    /** Blocks {@code username} (idempotent) and removes the follows between both, fixing the counters. */
    @Transactional
    public void block(Long blockerId, String username) {
        UserAccount blocked = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        if (blocked.getId().equals(blockerId)) {
            throw new BadRequestException("No puedes bloquearte a ti misma");
        }
        lockPair(blockerId, blocked.getId());

        blockRepository.insertIfAbsent(blockerId, blocked.getId());
        removeFollow(blockerId, blocked.getId());
        removeFollow(blocked.getId(), blockerId);
        followRequestRepository.deleteBetween(blockerId, blocked.getId());
    }

    @Transactional
    public void unblock(Long blockerId, String username) {
        UserAccount blocked = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        blockRepository.deleteBlock(blockerId, blocked.getId());
    }

    @Transactional(readOnly = true)
    public Page<BlockedUserDto> getBlocked(Long blockerId, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.clamp(pageable.getPageSize(), 1, MAX_PAGE_SIZE));
        return blockRepository.findBlockedBy(blockerId, page);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long a, Long b) {
        return blockRepository.existsEitherWay(a, b);
    }

    @Transactional(readOnly = true)
    public boolean hasBlocked(Long blockerId, Long blockedId) {
        return blockRepository.exists(blockerId, blockedId);
    }

    /** The ids among {@code others} with a block against {@code userId} in either direction (one query). */
    @Transactional(readOnly = true)
    public Set<Long> blockedEitherWayAmong(Long userId, Collection<Long> others) {
        if (others.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(blockRepository.findBlockedEitherWayAmong(userId, others));
    }

    /** The ids among {@code others} that blocked {@code userId}; to be answered as if they did not exist. */
    @Transactional(readOnly = true)
    public Set<Long> blockersAmong(Long userId, Collection<Long> others) {
        if (others.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(blockRepository.findBlockersAmong(userId, others));
    }

    /** Takes a transaction-scoped lock for the pair, so a block and a concurrent follow cannot interleave. */
    public void lockPair(Long a, Long b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        blockRepository.lockPair((low << 32) ^ high);
    }

    /** 403 with a neutral message when there is a block between both in either direction. */
    @Transactional(readOnly = true)
    public void requireNotBlocked(Long a, Long b) {
        if (blockRepository.existsEitherWay(a, b)) {
            throw new ForbiddenException(NOT_ALLOWED_MESSAGE);
        }
    }

    /**
     * Checks whether {@code initiator} may open a NEW private chat with {@code recipient}: no block and the
     * recipient's {@code dm_policy} (D-018). Same neutral 403 for every reason.
     */
    @Transactional(readOnly = true)
    public void requireCanStartPrivateChat(Long initiatorId, Long recipientId) {
        requireNotBlocked(initiatorId, recipientId);
        DmPolicy policy = userProfileRepository.findDmPolicy(recipientId).orElse(DmPolicy.EVERYONE);
        boolean allowed = switch (policy) {
            case EVERYONE -> true;
            case NOBODY -> false;
            case FOLLOWING -> userFollowRepository.existsByFollowerIdAndFollowingId(recipientId, initiatorId);
            case MUTUALS -> userFollowRepository.existsByFollowerIdAndFollowingId(recipientId, initiatorId)
                    && userFollowRepository.existsByFollowerIdAndFollowingId(initiatorId, recipientId);
        };
        if (!allowed) {
            throw new ForbiddenException(NOT_ALLOWED_MESSAGE);
        }
    }

    /**
     * Whether {@code initiator} may add all of {@code targets} to a group (also when creating it): no block in either
     * direction and each target's {@code dm_policy} as for a new private chat (T13). Nothing is added if any fails,
     * and the answer is the same neutral 403 for every reason (block or policy), so nobody can tell which applied.
     * Callers answer the targets that blocked the initiator with their 404 before calling this.
     */
    @Transactional(readOnly = true)
    public void requireCanAddToGroup(Long initiatorId, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        if (!blockedEitherWayAmong(initiatorId, targetIds).isEmpty()) {
            throw new ForbiddenException(NOT_ALLOWED_MESSAGE);
        }
        Set<Long> followers = null;
        Set<Long> followed = null;
        for (Object[] row : userProfileRepository.findDmPolicies(targetIds)) {
            Long targetId = (Long) row[0];
            DmPolicy policy = (DmPolicy) row[1];
            boolean allowed = switch (policy) {
                case EVERYONE -> true;
                case NOBODY -> false;
                case FOLLOWING -> {
                    followers = followers != null ? followers
                            : new HashSet<>(userFollowRepository.findFollowerIdsAmong(initiatorId, targetIds));
                    yield followers.contains(targetId);
                }
                case MUTUALS -> {
                    followers = followers != null ? followers
                            : new HashSet<>(userFollowRepository.findFollowerIdsAmong(initiatorId, targetIds));
                    followed = followed != null ? followed
                            : new HashSet<>(userFollowRepository.findFollowingIdsAmong(initiatorId, targetIds));
                    yield followers.contains(targetId) && followed.contains(targetId);
                }
            };
            if (!allowed) {
                throw new ForbiddenException(NOT_ALLOWED_MESSAGE);
            }
        }
    }

    private void removeFollow(Long followerId, Long followingId) {
        if (userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId) > 0) {
            userProfileRepository.decrementFollowing(followerId);
            userProfileRepository.decrementFollowers(followingId);
        }
    }
}
