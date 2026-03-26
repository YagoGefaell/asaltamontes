package io.github.yagogefaell.asaltamontes.message.controller;

import io.github.yagogefaell.asaltamontes.message.dto.PrivateMessage;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    public ChatController(SimpMessagingTemplate messagingTemplate, MessageService messageService) {
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
    }

    @MessageMapping("/chat.private")
    public void processPrivateMessage(@Payload PrivateMessage message) {
        messageService.saveMessage(message.conversationId(), message.senderUsername(), message.content());

        messagingTemplate.convertAndSendToUser(
                message.receiverUsername(),
                "/queue/messages",
                message
        );
    }
}