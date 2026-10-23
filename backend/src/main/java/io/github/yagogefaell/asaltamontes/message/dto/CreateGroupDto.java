package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

public record CreateGroupDto(
	@Size(max = 80, message = "El nombre del grupo no puede superar los 80 caracteres")
	String groupName,
	// Only a URL of one of our own uploads is accepted (BUG-057); groupMediaId is the preferred way
	@Size(max = 255, message = "La URL de la imagen no puede superar los 255 caracteres")
	String groupImage,
	UUID groupMediaId,
	List<String> users
) {}
