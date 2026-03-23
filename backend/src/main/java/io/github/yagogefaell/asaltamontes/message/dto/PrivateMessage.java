package io.github.yagogefaell.asaltamontes.message.dto;

public record PrivateMessage(
    String senderUsername, 
    String receiverUsername,
    String content
) {}
