package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;

/** Published when a message is saved; ChatMessageBroadcaster sends it once the transaction commits. */
public record MessageSentEvent(MessageDto message) {
}
