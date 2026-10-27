package io.github.yagogefaell.asaltamontes.message.service;

import java.util.List;
import java.util.UUID;

/**
 * Published in the transaction that adds people to a group (T12); T18 turns it into notifications. Internal ids:
 * this event never leaves the backend.
 */
public record AddedToGroup(UUID conversationId, String groupName, Long actorId, String actorUsername, List<Long> recipientIds) {
}
