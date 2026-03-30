package io.github.yagogefaell.asaltamontes.message.controller;

import io.github.yagogefaell.asaltamontes.message.dto.ChatReadEvent;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommand;
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
    public void processMessage(@Payload SendMessageCommand command, Principal principal) {
        messageService.processIncomingMessage(
                command.conversationId(),
                principal.getName(),
                command.content()
        );
    }

    @MessageMapping("/chat.read")
    public void processReadReceipt(@Payload ChatReadEvent message, Principal principal) {
        messageService.markConversationAsRead(message.conversationId(), principal.getName());
    }
}