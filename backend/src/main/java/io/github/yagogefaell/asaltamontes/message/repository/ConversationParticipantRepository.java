package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    List<ConversationParticipant> findByConversationId(Long conversationId);
    Optional<ConversationParticipant> findByUserUsername(String username);
    Optional<ConversationParticipant> findByConversationIdAndUserUsername(Long conversationId, String username);
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT p.user.id FROM ConversationParticipant p WHERE p.conversation.id = :conversationId")
    List<Long> findUserIdsByConversationId(@Param("conversationId") Long conversationId);

    // Single atomic statement: concurrent messages never lose an increment
    @Modifying
    @Query("""
        UPDATE ConversationParticipant p
        SET p.unreadCount = p.unreadCount + 1
        WHERE p.conversation.id = :conversationId AND p.user.id <> :senderId
    """)
    int incrementUnreadExceptSender(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    @Modifying
    @Query("""
        UPDATE ConversationParticipant p
        SET p.unreadCount = 0
        WHERE p.conversation.id = :conversationId AND p.user.id = :userId
    """)
    int resetUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}