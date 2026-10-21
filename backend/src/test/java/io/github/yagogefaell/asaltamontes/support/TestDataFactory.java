package io.github.yagogefaell.asaltamontes.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
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

    public String uniqueName(String prefix) {
        return prefix + "_" + System.nanoTime() % 1_000_000 + "_" + SEQUENCE.incrementAndGet();
    }

    /** Registers a user with profile and {@link #DEFAULT_PASSWORD}. */
    public UserAccount createUser() {
        return createUser(uniqueName("user"));
    }

    public UserAccount createUser(String username) {
        return userService.registerUserWithProfile(
                "Test User",
                username,
                username + "@test.local",
                DEFAULT_PASSWORD,
                DEFAULT_PASSWORD
        );
    }

    public Community createCommunity(UserAccount owner) {
        return communityService.create(new CreateCommunityDto(uniqueName("community"), "Test community", null), owner);
    }

    public Long createPrivateConversation(UserAccount user1, UserAccount user2) {
        return messageService.createOrGetPrivateConversation(user1.getUsername(), user2.getUsername()).conversationId();
    }
}
