package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    @Modifying
    @Query("""
        update UserProfile p
        set p.followersCount = p.followersCount + 1
        where p.user.id = :userId
    """)
    void incrementFollowers(@Param("userId") Long userId);

    @Modifying
    @Query("""
        update UserProfile p
        set p.followersCount = p.followersCount - 1
        where p.user.id = :userId and p.followersCount > 0
    """)
    void decrementFollowers(@Param("userId") Long userId);

    @Modifying
    @Query("""
        update UserProfile p
        set p.followingCount = p.followingCount + 1
        where p.user.id = :userId
    """)
    void incrementFollowing(@Param("userId") Long userId);

    @Modifying
    @Query("""
        update UserProfile p
        set p.followingCount = p.followingCount - 1
        where p.user.id = :userId and p.followingCount > 0
    """)
    void decrementFollowing(@Param("userId") Long userId);

    @Modifying
    @Query("""
        update UserProfile p
        set p.followingCount = (
            select count(f)
            from UserFollow f
            where f.follower.id = p.user.id
        )
        where p.user.id in :ids
    """)
    void recountFollowingByIds(List<Long> ids);

    @Modifying
    @Query("""
        update UserProfile p
        set p.followersCount = (
            select count(f)
            from UserFollow f
            where f.following.id = p.user.id
        )
        where p.user.id in :ids
    """)
    void recountFollowersByIds(List<Long> ids);
    
    @Query("""
        select p.user.id
        from UserProfile p
    """)
    Page<Long> findUserIds(Pageable pageable);

    @Query("select p.dmPolicy from UserProfile p where p.user.id = :userId")
    Optional<io.github.yagogefaell.asaltamontes.user.domain.DmPolicy> findDmPolicy(@Param("userId") Long userId);

    Optional<UserProfile> findByUserId(long userId);
    boolean existsByUserId(long userId);
    Optional<UserProfile> findByUser(io.github.yagogefaell.asaltamontes.user.domain.UserAccount user);
}