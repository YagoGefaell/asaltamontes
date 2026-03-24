package io.github.yagogefaell.asaltamontes.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.message.dto.ChatResponse;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummary;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/private/{recipientUsername}")
    public ResponseEntity<ChatResponse> getOrCreatePrivateChat(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String recipientUsername) {
        
        ChatResponse response = messageService.getOrCreatePrivateConversation(
                currentUser.getUsername(), 
                recipientUsername
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatSummary>> getMyConversations(
            @AuthenticationPrincipal UserDetails currentUser) {
        
        List<ChatSummary> summaries = messageService.getUserConversations(currentUser.getUsername());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageDto>> getChatHistory(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long conversationId) {
        List<MessageDto> history = messageService.getConversationHistory(
                conversationId, 
                currentUser.getUsername()
        );
        return ResponseEntity.ok(history);
    }
}
