package io.github.yagogefaell.asaltamontes.post.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.post.dto.CommentDto;
import io.github.yagogefaell.asaltamontes.post.dto.CreateCommentDto;
import io.github.yagogefaell.asaltamontes.post.dto.CreatePostDto;
import io.github.yagogefaell.asaltamontes.post.dto.LikeStateDto;
import io.github.yagogefaell.asaltamontes.post.dto.PostDto;
import io.github.yagogefaell.asaltamontes.post.service.PostService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Publications (T22a). Everything needs authentication (the app-wide rule outside /auth). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/posts")
    public ResponseEntity<PostDto> create(@AuthenticationPrincipal UserAccount currentUser, @Valid @RequestBody CreatePostDto dto) {
        PostDto created = postService.create(currentUser, dto);
        return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
    }

    @GetMapping("/posts/{publicId}")
    public ResponseEntity<PostDto> get(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId) {
        return ResponseEntity.ok(postService.get(currentUser, publicId));
    }

    @DeleteMapping("/posts/{publicId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId) {
        postService.delete(currentUser, publicId);
        return ResponseEntity.noContent().build();
    }

    /** Home: posts of her communities and of the people she follows (and hers), newest first. */
    @GetMapping("/feed")
    public ResponseEntity<CursorPageDto<PostDto>> feed(@AuthenticationPrincipal UserAccount currentUser,
                                                       @RequestParam(required = false) String before,
                                                       @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.feed(currentUser, before, limit));
    }

    @GetMapping("/communities/{slug}/posts")
    public ResponseEntity<CursorPageDto<PostDto>> ofCommunity(@AuthenticationPrincipal UserAccount currentUser,
                                                              @PathVariable String slug,
                                                              @RequestParam(required = false) String before,
                                                              @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.ofCommunity(currentUser, slug, before, limit));
    }

    @GetMapping("/users/{username}/posts")
    public ResponseEntity<CursorPageDto<PostDto>> ofUser(@AuthenticationPrincipal UserAccount currentUser,
                                                         @PathVariable String username,
                                                         @RequestParam(required = false) String before,
                                                         @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.ofUser(currentUser, username, before, limit));
    }

    /** Grid of Search: posts with a photo from public communities and profiles, minus what she already has in Home. */
    @GetMapping("/explore/posts")
    public ResponseEntity<CursorPageDto<PostDto>> explore(@AuthenticationPrincipal UserAccount currentUser,
                                                          @RequestParam(required = false) String before,
                                                          @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.explore(currentUser, before, limit));
    }

    @PutMapping("/posts/{publicId}/like")
    public ResponseEntity<LikeStateDto> like(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId) {
        return ResponseEntity.ok(postService.like(currentUser, publicId));
    }

    @DeleteMapping("/posts/{publicId}/like")
    public ResponseEntity<LikeStateDto> unlike(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId) {
        return ResponseEntity.ok(postService.unlike(currentUser, publicId));
    }

    @GetMapping("/posts/{publicId}/comments")
    public ResponseEntity<CursorPageDto<CommentDto>> comments(@AuthenticationPrincipal UserAccount currentUser,
                                                              @PathVariable UUID publicId,
                                                              @RequestParam(required = false) String after,
                                                              @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.comments(currentUser, publicId, after, limit));
    }

    @PostMapping("/posts/{publicId}/comments")
    public ResponseEntity<CommentDto> comment(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId,
                                              @RequestBody CreateCommentDto dto) {
        CommentDto created = postService.comment(currentUser, publicId, dto);
        return ResponseEntity.created(URI.create("/api/posts/" + publicId + "/comments/" + created.id())).body(created);
    }

    @DeleteMapping("/posts/{publicId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal UserAccount currentUser, @PathVariable UUID publicId,
                                              @PathVariable UUID commentId) {
        postService.deleteComment(currentUser, publicId, commentId);
        return ResponseEntity.noContent().build();
    }
}
