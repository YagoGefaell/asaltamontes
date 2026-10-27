package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // A group whose last member left disappears with its messages
    @Modifying
    @Query("DELETE FROM Message m WHERE m.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sender WHERE m.publicId = :publicId AND m.conversation.id = :conversationId")
    Optional<Message> findByPublicIdAndConversationId(@Param("publicId") UUID publicId, @Param("conversationId") Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    // A history cursor names a message of the same chat with that exact sent_at (D-021, T30c)
    @Query("""
        SELECT m.id FROM Message m
        WHERE m.publicId = :publicId AND m.conversation.id = :conversationId AND m.sentAt = :sentAt
    """)
    Optional<Long> findIdByPublicIdAndSentAt(@Param("publicId") UUID publicId, @Param("conversationId") Long conversationId,
                                             @Param("sentAt") java.time.LocalDateTime sentAt);

    // `since` hides what was sent before a member joined (T12); it is the epoch when the whole history is visible.
    // Cursor pagination by id, newest first (index messages (conversation_id, id DESC), T05).
    // Two queries instead of "(:before IS NULL OR ...)" so PostgreSQL always gets a typed parameter.
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.publicId, m.conversation.publicId, s.username, m.content, m.sentAt, m.type, m.systemPayload
        )
        FROM Message m LEFT JOIN m.sender s
        WHERE m.conversation.id = :conversationId AND m.sentAt >= :since
        ORDER BY m.id DESC
    """)
    List<MessageDto> findLatest(@Param("conversationId") Long conversationId, @Param("since") LocalDateTime since, Limit limit);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.publicId, m.conversation.publicId, s.username, m.content, m.sentAt, m.type, m.systemPayload
        )
        FROM Message m LEFT JOIN m.sender s
        WHERE m.conversation.id = :conversationId AND m.sentAt >= :since AND m.id < :before
        ORDER BY m.id DESC
    """)
    List<MessageDto> findBefore(@Param("conversationId") Long conversationId, @Param("since") LocalDateTime since,
                               @Param("before") Long before, Limit limit);

    // Messages newer than a known one, oldest first: catching up after a WebSocket reconnection (T11)
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.publicId, m.conversation.publicId, s.username, m.content, m.sentAt, m.type, m.systemPayload
        )
        FROM Message m LEFT JOIN m.sender s
        WHERE m.conversation.id = :conversationId AND m.sentAt >= :since AND m.id > :after
        ORDER BY m.id ASC
    """)
    List<MessageDto> findAfter(@Param("conversationId") Long conversationId, @Param("since") LocalDateTime since,
                              @Param("after") Long after, Limit limit);
}
