package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

import io.github.yagogefaell.asaltamontes.message.domain.ManagedBy;

/** What a managed group belongs to (T29r): a community (reached by {@code slug}) or an event (by {@code id}). */
public record GroupOriginDto(ManagedBy type, String slug, UUID id, String name) {}
