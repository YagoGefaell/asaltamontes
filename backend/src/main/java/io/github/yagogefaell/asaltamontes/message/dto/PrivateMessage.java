package io.github.yagogefaell.asaltamontes.message.dto;

public record PrivateMessage(
    Long conversationId,
    String senderUsername, 
    String receiverUsername,
    String content
) {}