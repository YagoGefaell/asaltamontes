package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

public record CreateGroupDto(
	String groupName,
	String groupImage,
	List<String> users
) {}
