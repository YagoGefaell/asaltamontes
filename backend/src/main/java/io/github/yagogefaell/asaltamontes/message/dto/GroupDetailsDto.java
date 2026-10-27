package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

import org.springframework.data.domain.Page;

import io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole;

/** Group info panel (T12). {@code imageMediaId} lets an admin keep or replace the image through the media flow. */
public record GroupDetailsDto(
    UUID id,
    String name,
    String imageUrl,
    UUID imageMediaId,
    int participantCount,
    ParticipantRole myRole,
    boolean historyVisibleToNew,
    boolean managed,
    Page<ParticipantDto> participants
) {}
