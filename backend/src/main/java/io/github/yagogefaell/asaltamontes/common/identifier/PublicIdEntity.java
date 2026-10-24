package io.github.yagogefaell.asaltamontes.common.identifier;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Entities exposed by the API (D-021): the {@code bigint} primary key stays internal and {@code publicId}, a UUID v7
 * assigned when the object is created, is the only identifier that leaves the backend. A new exposed entity extends
 * this class and its table gets {@code public_id uuid NOT NULL} with a unique index.
 */
@MappedSuperclass
@Getter
public abstract class PublicIdEntity {

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UuidV7.next();
}
