package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

public record CreateGroup(
	String groupName,
	String groupImage,
	List<String> users
) {}
