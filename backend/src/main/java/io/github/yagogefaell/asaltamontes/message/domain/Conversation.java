package io.github.yagogefaell.asaltamontes.message.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", nullable = false)
    private ChatType type;

    @Column(name = "group_name")
    private String groupName;

    /** URL of the "medium" variant of {@link #groupMediaId}, or a legacy external URL when that is null. */
    @Column(name = "group_image")
    private String groupImage;

    @Column(name = "group_media_id")
    private java.util.UUID groupMediaId;

    @Column(name = "last_message")
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_sender_id")
    private UserAccount lastSender;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConversationParticipant> participants = new ArrayList<>();
}