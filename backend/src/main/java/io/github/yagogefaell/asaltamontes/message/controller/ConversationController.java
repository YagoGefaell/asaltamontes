package io.github.yagogefaell.asaltamontes.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.message.dto.ChatResponseDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.message.dto.CreateGroupDto;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateRequestDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessagePageDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;


@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/private/{recipientUsername}")
    public ResponseEntity<ChatResponseDto> getPrivateChat(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String recipientUsername) {

        return messageService.getPrivateConversation(
                currentUser.getUsername(), 
                recipientUsername
        )
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/private")
    public ResponseEntity<ChatResponseDto> createOrGetPrivateChat(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody PrivateRequestDto request) {

        ChatResponseDto response = messageService.createOrGetPrivateConversation(
                currentUser.getUsername(),
                request.username()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/group")
    public ResponseEntity<ChatResponseDto> createGroupChat(
        @AuthenticationPrincipal UserDetails currentUser, 
        @RequestBody CreateGroupDto request
    ) {
        ChatResponseDto response = messageService.createGroupConversation(
            currentUser.getUsername(),
            request.groupName(),
            request.groupImage(),
            request.users()
        );
        return ResponseEntity.ok(response);
    }
    

    @GetMapping
    public ResponseEntity<List<ChatSummaryDto>> getMyConversations(
            @AuthenticationPrincipal UserDetails currentUser) {
        
        List<ChatSummaryDto> summaries = messageService.getUserConversations(currentUser.getUsername());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<MessagePageDto> getChatHistory(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "30") int limit) {
        if (after != null) {
            if (before != null) {
                throw new BadRequestException("Usa before o after, no los dos");
            }
            return ResponseEntity.ok(messageService.getMessagesAfter(conversationId, currentUser.getId(), after, limit));
        }
        MessagePageDto history = messageService.getConversationHistory(
                conversationId,
                currentUser.getId(),
                before,
                limit
        );
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long conversationId) {
        messageService.markConversationAsRead(conversationId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
