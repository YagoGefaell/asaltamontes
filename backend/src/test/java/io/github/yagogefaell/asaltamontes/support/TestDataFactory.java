package io.github.yagogefaell.asaltamontes.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;

/**
 * Creates test data through the real services, with unique names so tests can share the database.
 */
@TestComponent
@RequiredArgsConstructor
public class TestDataFactory {

    public static final String DEFAULT_PASSWORD = "password123";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final UserService userService;
    private final CommunityService communityService;
    private final MessageService messageService;
    private final UserProfileRepository profileRepository;

    public String uniqueName(String prefix) {
        return prefix + "_" + System.nanoTime() % 1_000_000 + "_" + SEQUENCE.incrementAndGet();
    }

    /**
     * Registers a user with a PUBLIC profile and {@link #DEFAULT_PASSWORD}. Registration itself creates private
     * profiles; since T23a that means follow requests, so the tests that are not about privacy start from public
     * ones. Use {@link #createPrivateUser()} for the others.
     */
    public UserAccount createUser() {
        return createUser(uniqueName("user"));
    }

    public UserAccount createUser(String username) {
        UserAccount user = createPrivateUser(username);
        profileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            profile.setIsPublicProfile(true);
            profileRepository.save(profile);
        });
        return user;
    }

    public UserAccount createPrivateUser() {
        return createPrivateUser(uniqueName("user"));
    }

    public UserAccount createPrivateUser(String username) {
        return userService.registerUserWithProfile(
                "Test User",
                username,
                username + "@test.local",
                DEFAULT_PASSWORD,
                DEFAULT_PASSWORD
        );
    }

    public Community createCommunity(UserAccount owner) {
        return communityService.create(new CreateCommunityDto(uniqueName("community"), "Test community", null, null), owner);
    }

    /** A manual group: {@code creator} (its admin) and the given members. */
    public UUID createGroup(UserAccount creator, UserAccount... members) {
        return messageService.createGroupConversation(creator.getUsername(), uniqueName("group"), null, null,
                java.util.Arrays.stream(members).map(UserAccount::getUsername).toList()).conversationId();
    }

    public UUID createPrivateConversation(UserAccount user1, UserAccount user2) {
        return messageService.createOrGetPrivateConversation(user1.getUsername(), user2.getUsername()).conversationId();
    }
}
