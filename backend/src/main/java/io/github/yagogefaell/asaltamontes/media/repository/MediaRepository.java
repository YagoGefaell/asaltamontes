package io.github.yagogefaell.asaltamontes.media.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yagogefaell.asaltamontes.media.domain.Media;

public interface MediaRepository extends JpaRepository<Media, UUID> {
}
