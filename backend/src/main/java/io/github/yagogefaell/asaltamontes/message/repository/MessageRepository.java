package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.Message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.username = :user1 AND m.recipient.username = :user2) OR " +
           "(m.sender.username = :user2 AND m.recipient.username = :user1) " +
           "ORDER BY m.sentAt ASC")
    List<Message> findChatHistory(@Param("user1") String user1, @Param("user2") String user2);
}