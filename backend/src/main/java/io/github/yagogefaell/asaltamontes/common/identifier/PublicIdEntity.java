package io.github.yagogefaell.asaltamontes.common.identifier;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

/**
 * Entities exposed by the API (D-021): the {@code bigint} primary key stays internal and {@code publicId}, a UUID v7
 * assigned when the row is inserted, is the only identifier that leaves the backend. A new exposed entity extends
 * this class and its table gets {@code public_id uuid NOT NULL} with a unique index.
 */
@MappedSuperclass
@Getter
public abstract class PublicIdEntity {

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    /** Assigned on insert, not on every instantiation: loading rows from the database must not burn ids. */
    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UuidV7.next();
        }
    }
}
