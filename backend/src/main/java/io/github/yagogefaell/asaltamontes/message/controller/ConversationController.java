package io.github.yagogefaell.asaltamontes.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.message.dto.ChatPageDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatResponseDto;
import io.github.yagogefaell.asaltamontes.message.dto.CreateGroupDto;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateRequestDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessagePageDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

import java.util.UUID;

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
        @jakarta.validation.Valid @RequestBody CreateGroupDto request
    ) {
        ChatResponseDto response = messageService.createGroupConversation(
            currentUser.getUsername(),
            request.groupName(),
            request.groupImage(),
            request.groupMediaId(),
            request.users()
        );
        return ResponseEntity.ok(response);
    }
    

    @GetMapping
    public ResponseEntity<ChatPageDto> getMyConversations(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") int limit) {

        ChatPageDto page = messageService.getUserConversations(currentUser.getUsername(), before, limit);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<MessagePageDto> getChatHistory(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "30") int limit) {
        if (after != null && !after.isBlank()) {
            if (before != null && !before.isBlank()) {
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
            @PathVariable UUID conversationId) {
        messageService.markConversationAsRead(conversationId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
