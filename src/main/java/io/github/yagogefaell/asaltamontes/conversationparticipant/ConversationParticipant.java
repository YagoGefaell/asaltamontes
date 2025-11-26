package io.github.yagogefaell.asaltamontes.conversationparticipant;

import io.github.yagogefaell.asaltamontes.conversation.Conversation;
import io.github.yagogefaell.asaltamontes.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="conversationparticipants", uniqueConstraints = @UniqueConstraint(columnNames =  {"conversation_id", "user_id"}))
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ParticipantRole role = ParticipantRole.MEMBER;

    private LocalDateTime joinedAt = LocalDateTime.now();

}
