package io.github.yagogefaell.asaltamontes.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.message.dto.ChatResponse;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummary;
import io.github.yagogefaell.asaltamontes.message.dto.CreateGroup;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateRequest;
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

    @GetMapping("/private")
    public ResponseEntity<ChatResponse> getPrivateChat(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody PrivateRequest request) {

        return messageService.getPrivateConversation(
                currentUser.getUsername(), 
                request.username()
        )
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/private")
    public ResponseEntity<ChatResponse> createPrivateChat(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody PrivateRequest request) {

        ChatResponse response = messageService.createPrivateConversation(
                currentUser.getUsername(),
                request.username()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
        @AuthenticationPrincipal UserDetails currentUser, 
        @RequestBody CreateGroup request
    ) {
        ChatResponse response = messageService.createGroupConversation(
            currentUser.getUsername(),
            request.groupName(),
            request.groupImage(),
            request.users()
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
