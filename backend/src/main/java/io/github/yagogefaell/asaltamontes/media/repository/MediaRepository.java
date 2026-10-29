package io.github.yagogefaell.asaltamontes.media.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.media.domain.Media;
import jakarta.persistence.LockModeType;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    @Query("select coalesce(sum(m.bytes), 0) from Media m where m.ownerId = :ownerId")
    long sumBytesByOwner(@Param("ownerId") Long ownerId);

    /** Linking takes a shared lock so the orphan cleanup (exclusive lock) cannot delete the media meanwhile. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from Media m where m.id = :id")
    Optional<Media> findByIdForShare(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Media m where m.id = :id")
    Optional<Media> findByIdForUpdate(@Param("id") UUID id);

    /** Every resource that can hold an image reference has to be listed here and in {@link #isReferenced}. */
    @Query("""
            select m.id from Media m
            where m.createdAt < :before
              and not exists (select 1 from UserProfile p where p.avatarMediaId = m.id)
              and not exists (select 1 from Community c where c.coverMediaId = m.id)
              and not exists (select 1 from Conversation v where v.groupMediaId = m.id)
              and not exists (select 1 from PostMedia pm where pm.mediaId = m.id)
            order by m.createdAt
            """)
    List<UUID> findUnreferencedCreatedBefore(@Param("before") Instant before, Pageable pageable);

    @Query("""
            select case when
                exists (select 1 from UserProfile p where p.avatarMediaId = :id)
                or exists (select 1 from Community c where c.coverMediaId = :id)
                or exists (select 1 from Conversation v where v.groupMediaId = :id)
                or exists (select 1 from PostMedia pm where pm.mediaId = :id)
            then true else false end
            from Media m where m.id = :id
            """)
    boolean isReferenced(@Param("id") UUID id);

    @Query("select m.id from Media m where m.ownerId = :ownerId order by m.createdAt")
    List<UUID> findIdsByOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    /**
     * Account deletion: clears the references (id and URL text) to the owner's images from resources that stay.
     * The FKs would null the ids on their own, but not the URL text columns.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserProfile p set p.avatarMediaId = null, p.profilePictureUrl = null
            where p.avatarMediaId in (select m.id from Media m where m.ownerId = :ownerId)
            """)
    int detachAvatarsOf(@Param("ownerId") Long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Community c set c.coverMediaId = null, c.imageUrl = null
            where c.coverMediaId in (select m.id from Media m where m.ownerId = :ownerId)
            """)
    int detachCoversOf(@Param("ownerId") Long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Conversation v set v.groupMediaId = null, v.groupImage = null
            where v.groupMediaId in (select m.id from Media m where m.ownerId = :ownerId)
            """)
    int detachGroupImagesOf(@Param("ownerId") Long ownerId);
}
