package io.github.yagogefaell.asaltamontes.message.domain;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import java.time.LocalDateTime;

@Entity
// Bulk statements (last message, unread counters) change columns behind the entity: write only what it changed
@org.hibernate.annotations.DynamicUpdate
@NoArgsConstructor
@Getter
@Setter
@Table(
    name = "conversation_participants",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_conversation_user", 
            columnNames = {"conversation_id", "user_id"}
        )
    }
)
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt = OpaqueCursor.truncate(LocalDateTime.now());
}