package io.github.yagogefaell.asaltamontes.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.message.dto.ChatResponseDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.message.dto.CreateGroupDto;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateRequestDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


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
