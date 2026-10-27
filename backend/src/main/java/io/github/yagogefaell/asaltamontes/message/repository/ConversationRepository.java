package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

import org.springframework.data.domain.Limit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // The API only knows public ids (D-021); the unique index on public_id serves these lookups
    @Query("SELECT c.id FROM Conversation c WHERE c.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    Optional<Conversation> findByPublicId(UUID publicId);

    // Row lock for the operations that count or change the members of a group (size cap, last admin)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.publicId = :publicId")
    Optional<Conversation> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    // A chat of the list cursor: it must be one of hers, so a cursor cannot probe other people's chats
    @Query("""
        SELECT c.id FROM Conversation c JOIN c.participants p
        WHERE c.publicId = :publicId AND p.user.username = :username
    """)
    Optional<Long> findIdByPublicIdAndParticipant(@Param("publicId") UUID publicId, @Param("username") String username);
    @Query("""
        SELECT DISTINCT c FROM Conversation c 
        JOIN c.participants p1 
        JOIN c.participants p2 
        WHERE c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL 
        AND p1.user.username = :user1 
        AND p2.user.username = :user2
    """)
    Optional<Conversation> findPrivateConversation(@Param("user1") String user1, @Param("user2") String user2);

    // Chat list by cursor over (last_message_at DESC NULLS LAST, id DESC) (T10b). PostgreSQL starts from the
    // participations of the user (idx_conversation_participants_user) and sorts only her chats: no index on
    // conversations helps here, and one on last_message_at would slow down every message (EXPLAIN in #44).
    // Three queries instead of optional parameters, so PostgreSQL always gets typed ones (as in MessageRepository).
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto(
            c.publicId,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupName 
                ELSE otherUser.username 
            END,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupImage 
                ELSE otherProfile.profilePictureUrl 
            END,
            c.type,
            me.unreadCount,
            c.lastMessage,
            lastSender.username,
            c.lastMessageAt
        )
        FROM Conversation c
        JOIN c.participants me ON me.user.username = :username
        LEFT JOIN c.lastSender lastSender
        LEFT JOIN c.participants other ON other.conversation.id = c.id
            AND other.user.username != :username
            AND c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL
        LEFT JOIN other.user otherUser
        LEFT JOIN otherUser.profile otherProfile
        WHERE me.user.username = :username
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
    """)
    List<ChatSummaryDto> findChatsForUser(@Param("username") String username, Limit limit);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto(
            c.publicId,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupName 
                ELSE otherUser.username 
            END,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupImage 
                ELSE otherProfile.profilePictureUrl 
            END,
            c.type,
            me.unreadCount,
            c.lastMessage,
            lastSender.username,
            c.lastMessageAt
        )
        FROM Conversation c
        JOIN c.participants me ON me.user.username = :username
        LEFT JOIN c.lastSender lastSender
        LEFT JOIN c.participants other ON other.conversation.id = c.id
            AND other.user.username != :username
            AND c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL
        LEFT JOIN other.user otherUser
        LEFT JOIN otherUser.profile otherProfile
        WHERE me.user.username = :username
          AND (c.lastMessageAt < :beforeAt
               OR (c.lastMessageAt = :beforeAt AND c.id < :beforeId)
               OR c.lastMessageAt IS NULL)
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
    """)
    List<ChatSummaryDto> findChatsForUserBefore(@Param("username") String username, @Param("beforeAt") LocalDateTime beforeAt,
                                                @Param("beforeId") Long beforeId, Limit limit);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto(
            c.publicId,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupName 
                ELSE otherUser.username 
            END,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupImage 
                ELSE otherProfile.profilePictureUrl 
            END,
            c.type,
            me.unreadCount,
            c.lastMessage,
            lastSender.username,
            c.lastMessageAt
        )
        FROM Conversation c
        JOIN c.participants me ON me.user.username = :username
        LEFT JOIN c.lastSender lastSender
        LEFT JOIN c.participants other ON other.conversation.id = c.id
            AND other.user.username != :username
            AND c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL
        LEFT JOIN other.user otherUser
        LEFT JOIN otherUser.profile otherProfile
        WHERE me.user.username = :username
          AND c.lastMessageAt IS NULL AND c.id < :beforeId
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
    """)
    // The cursor is already among the chats without messages (they go last)
    List<ChatSummaryDto> findChatsWithoutMessagesForUserBefore(@Param("username") String username, @Param("beforeId") Long beforeId,
                                                               Limit limit);

    // One statement, and never overwritten by a message sent earlier that commits later
    @Modifying
    @Query("""
        UPDATE Conversation c
        SET c.lastMessage = :preview, c.lastMessageAt = :sentAt, c.lastSender = :sender
        WHERE c.id = :conversationId AND (c.lastMessageAt IS NULL OR c.lastMessageAt <= :sentAt)
    """)
    int updateLastMessage(@Param("conversationId") Long conversationId, @Param("preview") String preview,
                          @Param("sentAt") LocalDateTime sentAt, @Param("sender") UserAccount sender);
}
