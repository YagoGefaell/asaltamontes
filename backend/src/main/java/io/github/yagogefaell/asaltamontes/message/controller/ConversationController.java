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

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.message.dto.AddParticipantsDto;
import io.github.yagogefaell.asaltamontes.message.dto.GroupDetailsDto;
import io.github.yagogefaell.asaltamontes.message.dto.UpdateRoleDto;
import io.github.yagogefaell.asaltamontes.message.service.GroupService;
import io.github.yagogefaell.asaltamontes.message.service.UpdateGroupReader;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;

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
    private final GroupService groupService;
    private final UpdateGroupReader updateGroupReader;

    public ConversationController(MessageService messageService, GroupService groupService,
                                  UpdateGroupReader updateGroupReader) {
        this.messageService = messageService;
        this.groupService = groupService;
        this.updateGroupReader = updateGroupReader;
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

    // --- Group management (T12): all of them answer 404 for chats that are not groups the caller is in ---

    @GetMapping("/{conversationId}")
    public ResponseEntity<GroupDetailsDto> getGroup(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(groupService.getDetails(conversationId, currentUser, page, size));
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<GroupDetailsDto> updateGroup(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @RequestBody JsonNode body) {
        // The raw JSON tells an absent field (no change) from an explicit null (clears it), D-020
        return ResponseEntity.ok(groupService.update(conversationId, updateGroupReader.read(body), currentUser));
    }

    @PostMapping("/{conversationId}/participants")
    public ResponseEntity<GroupDetailsDto> addParticipants(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AddParticipantsDto request) {
        return ResponseEntity.ok(groupService.addParticipants(conversationId, request.usernames(), currentUser));
    }

    @DeleteMapping("/{conversationId}/participants/{username}")
    public ResponseEntity<Void> removeParticipant(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @PathVariable String username) {
        groupService.removeParticipant(conversationId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{conversationId}/participants/{username}")
    public ResponseEntity<Void> changeRole(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId,
            @PathVariable String username,
            @Valid @RequestBody UpdateRoleDto request) {
        groupService.changeRole(conversationId, username, request.role(), currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID conversationId) {
        groupService.leave(conversationId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
