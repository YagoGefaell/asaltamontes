package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserBlock;
import io.github.yagogefaell.asaltamontes.user.dto.BlockedUserDto;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

    @Query("""
        SELECT COUNT(b) > 0 FROM UserBlock b
        WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId
    """)
    boolean exists(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query("""
        SELECT COUNT(b) > 0 FROM UserBlock b
        WHERE (b.blocker.id = :a AND b.blocked.id = :b) OR (b.blocker.id = :b AND b.blocked.id = :a)
    """)
    boolean existsEitherWay(@Param("a") Long a, @Param("b") Long b);

    /** The ids among {@code others} that have a block with {@code userId} in either direction (one query). */
    @Query("""
        SELECT CASE WHEN b.blocker.id = :userId THEN b.blocked.id ELSE b.blocker.id END
        FROM UserBlock b
        WHERE (b.blocker.id = :userId AND b.blocked.id IN :others) OR (b.blocked.id = :userId AND b.blocker.id IN :others)
    """)
    List<Long> findBlockedEitherWayAmong(@Param("userId") Long userId, @Param("others") Collection<Long> others);

    // Native and conflict-tolerant: a concurrent identical request must not abort the transaction
    @Modifying
    @Query(value = "INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES (:blockerId, :blockedId, now()) ON CONFLICT DO NOTHING",
           nativeQuery = true)
    int insertIfAbsent(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query(value = """
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.BlockedUserDto(
            u.username, p.fullName, p.profilePictureUrl, b.createdAt)
        FROM UserBlock b JOIN b.blocked u LEFT JOIN u.profile p
        WHERE b.blocker.id = :blockerId
        ORDER BY b.createdAt DESC, u.username
    """, countQuery = "SELECT COUNT(b) FROM UserBlock b WHERE b.blocker.id = :blockerId")
    Page<BlockedUserDto> findBlockedBy(@Param("blockerId") Long blockerId, Pageable pageable);
}
