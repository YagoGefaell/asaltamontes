package io.github.yagogefaell.asaltamontes.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yagogefaell.asaltamontes.post.domain.PostMedia;

public interface PostMediaRepository extends JpaRepository<PostMedia, PostMedia.Key> {
}
