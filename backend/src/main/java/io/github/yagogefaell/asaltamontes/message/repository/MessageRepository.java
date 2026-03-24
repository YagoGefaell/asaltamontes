package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.MessageDto(
            m.id,
            m.conversation.id,
            m.sender.username,
            m.content,
            m.sentAt
        )
        FROM Message m
        WHERE m.conversation.id = :conversationId
        ORDER BY m.sentAt ASC
    """)
    List<MessageDto> findMessagesByConversationId(@Param("conversationId") Long conversationId);
}