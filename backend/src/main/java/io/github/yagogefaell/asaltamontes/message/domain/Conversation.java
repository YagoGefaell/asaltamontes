package io.github.yagogefaell.asaltamontes.message.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@Entity
// Bulk statements (last message, unread counters) change columns behind the entity: write only what it changed
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@Table(name = "conversations")
public class Conversation extends PublicIdEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private UserAccount createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "managed_by", nullable = false)
    private ManagedBy managedBy = ManagedBy.NONE;

    /** Internal reference of the community/event that manages the group (T20/T21); never exposed. */
    @Column(name = "managed_ref_id")
    private Long managedRefId;

    /** Manual groups only: whether people who join later can read what was sent before. */
    @Column(name = "history_visible_to_new", nullable = false)
    private boolean historyVisibleToNew = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConversationParticipant> participants = new ArrayList<>();
}