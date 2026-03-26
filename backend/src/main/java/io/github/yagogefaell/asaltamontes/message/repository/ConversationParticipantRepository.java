package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    List<ConversationParticipant> findByConversationId(Long conversationId);
    Optional<ConversationParticipant> findByUserUsername(String username);
    Optional<ConversationParticipant> findByConversationIdAndUserUsername(Long conversationId, String username);
}