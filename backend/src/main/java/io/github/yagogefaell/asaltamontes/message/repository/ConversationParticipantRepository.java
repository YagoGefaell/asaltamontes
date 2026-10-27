package io.github.yagogefaell.asaltamontes.message.repository;

import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.dto.ParticipantDto;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    List<ConversationParticipant> findByConversationId(Long conversationId);
    Optional<ConversationParticipant> findByUserUsername(String username);
    Optional<ConversationParticipant> findByConversationIdAndUserUsername(Long conversationId, String username);
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT p.user.id FROM ConversationParticipant p WHERE p.conversation.publicId = :conversationPublicId")
    List<Long> findUserIdsByConversationPublicId(@Param("conversationPublicId") UUID conversationPublicId);

    // Single atomic statement: concurrent messages never lose an increment
    @Modifying
    @Query("""
        UPDATE ConversationParticipant p
        SET p.unreadCount = p.unreadCount + 1
        WHERE p.conversation.id = :conversationId AND p.user.id <> :senderId
    """)
    int incrementUnreadExceptSender(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    // The other member of a private chat (empty for groups), to apply blocks when sending (T13)
    @Query("""
        SELECT p.user.id FROM ConversationParticipant p
        WHERE p.conversation.id = :conversationId AND p.user.id <> :userId
          AND p.conversation.type = io.github.yagogefaell.asaltamontes.message.domain.ChatType.INDIVIDUAL
    """)
    Optional<Long> findOtherUserIdInPrivateChat(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    // The group's members, admins first, then by seniority (T12). One page of at most 50 (the controller clamps it).
    @Query(value = """
        SELECT new io.github.yagogefaell.asaltamontes.message.dto.ParticipantDto(
            u.username, pr.fullName, pr.profilePictureUrl, p.role, p.joinedAt)
        FROM ConversationParticipant p JOIN p.user u LEFT JOIN u.profile pr
        WHERE p.conversation.id = :conversationId
        ORDER BY CASE WHEN p.role = io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole.ADMIN THEN 0 ELSE 1 END,
                 p.joinedAt, p.id
    """, countQuery = "SELECT COUNT(p) FROM ConversationParticipant p WHERE p.conversation.id = :conversationId")
    Page<ParticipantDto> findParticipantPage(@Param("conversationId") Long conversationId, Pageable pageable);

    // The member who has been in the group the longest, to be promoted when the last admin leaves (excluding her)
    @Query("""
        SELECT p FROM ConversationParticipant p
        WHERE p.conversation.id = :conversationId AND p.user.id <> :excludedUserId
        ORDER BY p.joinedAt, p.id
    """)
    List<ConversationParticipant> findOldestExcluding(@Param("conversationId") Long conversationId,
                                                      @Param("excludedUserId") Long excludedUserId, Limit limit);

    @Query("SELECT COUNT(p) FROM ConversationParticipant p WHERE p.conversation.id = :conversationId AND p.role = io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole.ADMIN")
    long countAdmins(@Param("conversationId") Long conversationId);

    @Query("SELECT p.user.id FROM ConversationParticipant p WHERE p.conversation.id = :conversationId AND p.user.id IN :userIds")
    List<Long> findParticipantUserIds(@Param("conversationId") Long conversationId, @Param("userIds") java.util.Collection<Long> userIds);

    @Query("SELECT p FROM ConversationParticipant p WHERE p.conversation.id = :conversationId AND LOWER(p.user.username) = :username")
    Optional<ConversationParticipant> findByConversationIdAndLowerUsername(@Param("conversationId") Long conversationId, @Param("username") String username);

    @Modifying
    @Query("""
        UPDATE ConversationParticipant p
        SET p.unreadCount = 0
        WHERE p.conversation.id = :conversationId AND p.user.id = :userId
    """)
    int resetUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    // Badge of the navigation bar: chats with something unread, including those not loaded yet (T10b)
    @Query("SELECT COUNT(p) FROM ConversationParticipant p WHERE p.user.username = :username AND p.unreadCount > 0")
    long countUnreadChats(@Param("username") String username);
}
