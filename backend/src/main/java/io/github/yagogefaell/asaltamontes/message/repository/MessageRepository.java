package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Cursor pagination by id, newest first (index messages (conversation_id, id DESC), T05).
    // Two queries instead of "(:before IS NULL OR ...)" so PostgreSQL always gets a typed parameter.
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.id, m.conversation.id, m.sender.username, m.content, m.sentAt
        )
        FROM Message m
        WHERE m.conversation.id = :conversationId
        ORDER BY m.id DESC
    """)
    List<MessageDto> findLatest(@Param("conversationId") Long conversationId, Limit limit);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.id, m.conversation.id, m.sender.username, m.content, m.sentAt
        )
        FROM Message m
        WHERE m.conversation.id = :conversationId AND m.id < :before
        ORDER BY m.id DESC
    """)
    List<MessageDto> findBefore(@Param("conversationId") Long conversationId, @Param("before") Long before, Limit limit);

    // Messages newer than a known one, oldest first: catching up after a WebSocket reconnection (T11)
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.id, m.conversation.id, m.sender.username, m.content, m.sentAt
        )
        FROM Message m
        WHERE m.conversation.id = :conversationId AND m.id > :after
        ORDER BY m.id ASC
    """)
    List<MessageDto> findAfter(@Param("conversationId") Long conversationId, @Param("after") Long after, Limit limit);
}
