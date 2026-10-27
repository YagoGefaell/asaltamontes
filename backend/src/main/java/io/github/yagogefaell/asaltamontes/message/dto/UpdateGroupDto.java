package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Size;

/**
 * Changes to a group (PATCH, D-020): an absent field stays as it is; an explicit {@code null} in the image fields
 * removes the image; the name is required, so {@code null} or blank is a 400. {@code present} holds the names of the
 * fields sent, which a record alone cannot tell from null.
 */
public record UpdateGroupDto(
    @Size(max = 80, message = "El nombre del grupo no puede superar los 80 caracteres")
    String groupName,

    @Size(max = 255, message = "La URL de la imagen no puede superar los 255 caracteres")
    String groupImage,

    java.util.UUID groupMediaId,

    Boolean historyVisibleToNew,

    @JsonIgnore
    Set<String> present
) {
    public boolean has(String field) {
        return present != null && present.contains(field);
    }

    public UpdateGroupDto withPresent(Set<String> fields) {
        return new UpdateGroupDto(groupName, groupImage, groupMediaId, historyVisibleToNew, Set.copyOf(fields));
    }
}
