package io.github.yagogefaell.asaltamontes.message.controller;

import io.github.yagogefaell.asaltamontes.message.dto.ChatReadEventDto;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommandDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final MessageService messageService;

    public ChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send")
    public void processMessage(@Payload SendMessageCommandDto command, Principal principal) {
        messageService.processIncomingMessage(
                command.conversationId(),
                principal.getName(),
                command.content()
        );
    }

    @MessageMapping("/chat.read")
    public void processReadReceipt(@Payload ChatReadEventDto message, Principal principal) {
        messageService.markConversationAsRead(message.conversationId(), principal.getName());
    }
}