package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

/**
 * The chat of an event (T21). {@code readOnlyReason} is null while she can write, {@code CANCELLED} once the event
 * was cancelled and {@code ARCHIVED} from 7 days after it ended: the history stays readable.
 */
public record EventChatDto(UUID conversationId, boolean canWrite, String readOnlyReason) {
}
