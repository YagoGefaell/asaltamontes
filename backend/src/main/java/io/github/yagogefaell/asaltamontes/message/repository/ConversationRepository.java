package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("""
        SELECT DISTINCT c FROM Conversation c 
        JOIN c.participants p1 
        JOIN c.participants p2 
        WHERE c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL 
        AND p1.user.username = :user1 
        AND p2.user.username = :user2
    """)
    Optional<Conversation> findPrivateConversation(@Param("user1") String user1, @Param("user2") String user2);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.ChatSummary(
            c.id,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupName 
                ELSE other.user.username 
            END,
            CASE 
                WHEN c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.GROUP THEN c.groupImage 
                ELSE other.user.profile.profilePictureUrl 
            END,
            c.type,
            me.unreadCount,
            c.lastMessage,
            c.lastSender.username,
            c.lastSender.id,
            c.lastMessageAt
        )
        FROM Conversation c
        JOIN c.participants me ON me.user.username = :username
        LEFT JOIN c.participants other ON other.conversation.id = c.id 
            AND other.user.username != :username 
            AND c.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL
        WHERE me.user.username = :username
        ORDER BY c.lastMessageAt DESC NULLS LAST
    """)
    List<ChatSummary> findActiveChatsForUser(@Param("username") String username);
}