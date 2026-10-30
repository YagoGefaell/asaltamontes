package io.github.yagogefaell.asaltamontes.user.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.common.exceptions.AlreadyFollowingException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.exceptions.SelfFollowException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserFollow;
import io.github.yagogefaell.asaltamontes.user.dto.FollowRequestDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import io.github.yagogefaell.asaltamontes.user.repository.FollowRequestRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserFollowRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final String REQUEST_NOT_FOUND = "Solicitud no encontrada";

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;
    private final BlockService blockService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserActionLimiter limiter;
    private final FollowRequestRepository followRequestRepository;
    private final ProfileAccess profileAccess;

    /** What following someone did: she follows now, or (private profile) a request is waiting for approval. */
    public enum FollowOutcome { FOLLOWING, REQUESTED }

    @Transactional
    public FollowOutcome followUser(Long followerId, String usernameToFollow) {
        limiter.follow(followerId);
        UserAccount following = getUserToFollowOrThrow(followerId, usernameToFollow);

        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, following.getId())) {
            throw new AlreadyFollowingException(usernameToFollow);
        }

        if (!userProfileRepository.findIsPublicProfile(following.getId()).orElse(false)) {
            // Private profile (T23a): a request instead of a follow. Asking twice is idempotent (same answer, no
            // second notification); the unique constraint makes concurrent calls safe.
            if (followRequestRepository.insertIfAbsent(followerId, following.getId()) > 0) {
                eventPublisher.publishEvent(new FollowRequested(followerId, following.getId()));
            }
            return FollowOutcome.REQUESTED;
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
        eventPublisher.publishEvent(new UserFollowed(followerId, following.getId()));
        return FollowOutcome.FOLLOWING;
    }

    /** Unfollows and, if there is only a pending request, cancels it. Both are idempotent. */
    @Transactional
    public void unfollowUser(Long followerId, String usernameToUnfollow) {
        Long followingId = userAccountRepository.findByUsernameIgnoreCase(usernameToUnfollow)
                .orElseThrow(() -> new UserNotFoundException(usernameToUnfollow))
                .getId();

        // Only the transaction that really deletes the row (count > 0) touches the counters. Concurrent deletes of
        // the same row (a second unfollow or BlockService.removeFollow) serialise on the row lock, so exactly one wins.
        if (userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId) > 0) {
            userProfileRepository.decrementFollowing(followerId);
            userProfileRepository.decrementFollowers(followingId);
        }
        followRequestRepository.deleteRequest(followerId, followingId);
    }

    // ---------------------------------------------------------------- follow requests (T23a)

    @Transactional(readOnly = true)
    public Page<FollowRequestDto> getRequests(Long ownerId, Pageable pageable) {
        return followRequestRepository.findInbox(ownerId, pageable);
    }

    /**
     * Turns the request into a follow. The request row is deleted first: only the call that really deleted it goes
     * on, so approving twice (or while she cancels) cannot count the follower twice.
     */
    @Transactional
    public void approveRequest(Long ownerId, String requesterUsername) {
        UserAccount requester = findRequester(ownerId, requesterUsername);
        if (followRequestRepository.deleteRequest(requester.getId(), ownerId) == 0) {
            throw new NotFoundException(REQUEST_NOT_FOUND);
        }
        if (!userFollowRepository.insertFollowers(ownerId, new Long[] {requester.getId()}).isEmpty()) {
            userProfileRepository.incrementFollowing(requester.getId());
            userProfileRepository.incrementFollowers(ownerId);
        }
        eventPublisher.publishEvent(new FollowRequestApproved(ownerId, requester.getId()));
    }

    /** Deletes the request without telling her. */
    @Transactional
    public void rejectRequest(Long ownerId, String requesterUsername) {
        UserAccount requester = findRequester(ownerId, requesterUsername);
        if (followRequestRepository.deleteRequest(requester.getId(), ownerId) == 0) {
            throw new NotFoundException(REQUEST_NOT_FOUND);
        }
    }

    /** Her profile became public: every pending request is accepted (called from the profile edit). */
    @Transactional
    public void approveAllPending(Long ownerId) {
        List<Long> requesters = followRequestRepository.deleteAllTo(ownerId);
        if (requesters.isEmpty()) {
            return;
        }
        List<Long> created = userFollowRepository.insertFollowers(ownerId, requesters.toArray(Long[]::new));
        if (!created.isEmpty()) {
            userProfileRepository.incrementFollowingByIds(created);
            userProfileRepository.addFollowers(ownerId, created.size());
        }
        requesters.forEach(id -> eventPublisher.publishEvent(new FollowRequestApproved(ownerId, id)));
    }

    /** An unknown user and one with a block either way look the same: no such request. */
    private UserAccount findRequester(Long ownerId, String username) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .filter(u -> !blockService.isBlockedEitherWay(ownerId, u.getId()))
                .orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
    }

    private UserAccount getUserToFollowOrThrow(Long followerId, String username) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (followerId.equals(user.getId())) {
            throw new SelfFollowException();
        }
        requireNoBlock(followerId, user);

        return user;
    }

    /** Who blocked the requester looks like an unknown user; if the requester blocked her, she must unblock first. */
    private void requireNoBlock(Long requesterId, UserAccount other) {
        blockService.lockPair(requesterId, other.getId());
        if (blockService.hasBlocked(other.getId(), requesterId)) {
            throw new UserNotFoundException(other.getUsername());
        }
        if (blockService.hasBlocked(requesterId, other.getId())) {
            throw new ForbiddenException("Desbloquea a esta usuaria para poder seguirla");
        }
    }

    /** The lists of someone who blocked the requester are not visible to her (404). */
    private void requireVisible(String username, Long currentUserId) {
        userAccountRepository.findByUsernameIgnoreCase(username)
                .filter(u -> blockService.hasBlocked(u.getId(), currentUserId))
                .ifPresent(u -> {
                    throw new UserNotFoundException(username);
                });
    }

    /** The lists of a private profile are for herself and her accepted followers (T23a); the rest get an empty page. */
    private boolean canViewLists(String username, Long currentUserId) {
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .map(u -> profileAccess.canViewContent(currentUserId, u.getId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Page<UserSearchDto> getUsersFollowedBy(String username, Long currentUserId, Pageable pageable) {
        requireVisible(username, currentUserId);
        if (!canViewLists(username, currentUserId)) {
            return Page.empty(pageable);
        }
        return userFollowRepository.findFollowingByUsername(username, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserSearchDto> getUsersFollowing(String username, Long currentUserId, Pageable pageable) {
        requireVisible(username, currentUserId);
        if (!canViewLists(username, currentUserId)) {
            return Page.empty(pageable);
        }
        return userFollowRepository.findFollowersByUsername(username, currentUserId, pageable);
    }
}
