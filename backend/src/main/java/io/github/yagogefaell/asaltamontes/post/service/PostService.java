package io.github.yagogefaell.asaltamontes.post.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.dto.MediaUrlsDto;
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.media.storage.MediaStorage;
import io.github.yagogefaell.asaltamontes.post.domain.Post;
import io.github.yagogefaell.asaltamontes.post.domain.PostComment;
import io.github.yagogefaell.asaltamontes.post.dto.CommentDto;
import io.github.yagogefaell.asaltamontes.post.dto.CreateCommentDto;
import io.github.yagogefaell.asaltamontes.post.dto.LikeStateDto;
import io.github.yagogefaell.asaltamontes.post.repository.PostCommentRepository;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository.CommentRow;
import io.github.yagogefaell.asaltamontes.post.domain.PostMedia;
import io.github.yagogefaell.asaltamontes.post.dto.CreatePostDto;
import io.github.yagogefaell.asaltamontes.post.dto.PostDto;
import io.github.yagogefaell.asaltamontes.post.repository.PostMediaRepository;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository.MediaRow;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository.PostRow;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository.Scope;
import io.github.yagogefaell.asaltamontes.post.repository.PostRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;

/**
 * Publications (T22a1): create, read, list and delete. Who sees what is decided by the query
 * ({@link PostQueryRepository}); anything the viewer may not see answers the same 404 as a post that does not exist.
 * In a community only the owner and the moderators post (D-025); anybody posts on her own profile.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    public static final int DEFAULT_PAGE = 20;
    public static final int MAX_PAGE = 50;
    public static final int MAX_MEDIA = 4;
    public static final int MAX_COMMENT = 1000;

    private static final String POST_NOT_FOUND = "Publicación no encontrada";
    private static final String COMMUNITY_NOT_FOUND = "Comunidad no encontrada";
    private static final String COMMENT_NOT_FOUND = "Comentario no encontrado";
    private static final String USER_NOT_FOUND = "Usuaria no encontrada";

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostQueryRepository queries;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserBlockRepository blockRepository;
    private final MediaReferenceService mediaReferences;
    private final MediaCleanupService mediaCleanup;
    private final MediaStorage storage;
    private final UserActionLimiter limiter;
    private final PostCommentRepository commentRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public PostDto create(UserAccount user, CreatePostDto dto) {
        // Every attempt counts, also the ones that end in an error
        limiter.postCreate(user.getId());
        String body = dto.body() == null ? "" : dto.body().strip();
        if (body.isEmpty()) {
            throw new BadRequestException("Escribe algo para publicar");
        }
        if (body.length() > 2000) {
            throw new BadRequestException("La publicación no puede superar los 2000 caracteres");
        }
        List<UUID> mediaIds = dto.mediaIds() == null ? List.of() : dto.mediaIds();
        if (mediaIds.size() > MAX_MEDIA || mediaIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw new BadRequestException("Máximo " + MAX_MEDIA + " fotos distintas por publicación");
        }

        Long communityId = null;
        if (dto.communitySlug() != null && !dto.communitySlug().isBlank()) {
            communityId = communityRepository.findIdBySlug(dto.communitySlug())
                    .orElseThrow(() -> new NotFoundException(COMMUNITY_NOT_FOUND));
            // Only the owner and the moderators publish in a community (D-025)
            if (!memberRepository.isModerator(communityId, user.getId())) {
                // Whoever cannot even see the community gets its 404; the rest, a 403
                requireVisible(communityId, user.getId());
                throw new ForbiddenException("Solo la propietaria y las moderadoras publican en esta comunidad");
            }
        }

        // Links inside this transaction: the media rows stay share-locked until the post is committed
        List<UUID> linked = new ArrayList<>();
        for (UUID mediaId : mediaIds) {
            linked.add(mediaReferences.link(mediaId, null, MediaPurpose.POST_IMAGE, user.getId(), null).mediaId());
        }

        Post post = postRepository.saveAndFlush(new Post(user.getId(), communityId, body));
        for (int i = 0; i < linked.size(); i++) {
            postMediaRepository.save(new PostMedia(post.getId(), i, linked.get(i)));
        }
        postMediaRepository.flush();
        return get(user, post.getPublicId());
    }

    @Transactional(readOnly = true)
    public PostDto get(UserAccount viewer, UUID publicId) {
        PostRow row = queries.one(viewer.getId(), publicId, true).orElseThrow(() -> new NotFoundException(POST_NOT_FOUND));
        return assemble(List.of(row)).get(0);
    }

    @Transactional(readOnly = true)
    public CursorPageDto<PostDto> feed(UserAccount viewer, String before, Integer limit) {
        return page(Scope.FEED, viewer, null, before, limit);
    }

    @Transactional(readOnly = true)
    public CursorPageDto<PostDto> ofCommunity(UserAccount viewer, String slug, String before, Integer limit) {
        Long communityId = communityRepository.findIdBySlug(slug).orElseThrow(() -> new NotFoundException(COMMUNITY_NOT_FOUND));
        // Public: any user with a session; private: members only. Banned users get the 404 of a missing community
        requireVisible(communityId, viewer.getId());
        return page(Scope.COMMUNITY, viewer, communityId, before, limit);
    }

    /** Posts on the profile of {@code username}; who may see them is decided in {@link PostQueryRepository} (D-025). */
    @Transactional(readOnly = true)
    public CursorPageDto<PostDto> ofUser(UserAccount viewer, String username, String before, Integer limit) {
        UserAccount author = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
        if (!author.getId().equals(viewer.getId()) && blockRepository.existsEitherWay(viewer.getId(), author.getId())) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return page(Scope.PROFILE, viewer, author.getId(), before, limit);
    }

    /** The author, or the owner or a moderator of the community for a community post. */
    @Transactional
    public void delete(UserAccount user, UUID publicId) {
        PostRow row = queries.one(user.getId(), publicId, true).orElseThrow(() -> new NotFoundException(POST_NOT_FOUND));
        if (!row.canDelete()) {
            throw new ForbiddenException("No puedes borrar esta publicación");
        }
        List<UUID> mediaIds = postRepository.findMediaIds(row.postId());
        if (postRepository.markDeleted(row.postId(), Instant.now()) == 0) {
            throw new NotFoundException(POST_NOT_FOUND);
        }
        // The photos are released; whatever nothing else references is deleted (files after the commit)
        postRepository.deleteMedia(row.postId());
        mediaIds.forEach(id -> mediaCleanup.deleteIfUnreferenced(id, null));
    }

    /** Posts with a photo from public communities and profiles that are not already in her home feed (T22a2). */
    @Transactional(readOnly = true)
    public CursorPageDto<PostDto> explore(UserAccount viewer, String before, Integer limit) {
        return page(Scope.EXPLORE, viewer, null, before, limit);
    }

    // ---------------------------------------------------------------- likes (T22a2)

    /**
     * Idempotent. Members of the community (muted ones too) like; non-members only look (403). On a profile post,
     * whoever can see it. The counter moves only when the like row was really inserted, in the same transaction.
     */
    @Transactional
    public LikeStateDto like(UserAccount user, UUID publicId) {
        limiter.postLike(user.getId());
        PostRow row = interactable(user, publicId);
        requireMember(row, user, false);
        if (postRepository.insertLike(row.postId(), user.getId()) == 1) {
            postRepository.addToLikeCount(row.postId(), 1);
        } else if (!postRepository.isLikedBy(row.postId(), user.getId())) {
            // Not a duplicate: the statement refused it (she was banned a moment ago, or the post was just deleted)
            throw new NotFoundException(POST_NOT_FOUND);
        }
        return new LikeStateDto(true, postRepository.likeCountOf(row.postId()));
    }

    /** Idempotent; whoever can still see the post may withdraw her like. */
    @Transactional
    public LikeStateDto unlike(UserAccount user, UUID publicId) {
        limiter.postLike(user.getId());
        PostRow row = interactable(user, publicId);
        if (postRepository.deleteLike(row.postId(), user.getId()) == 1) {
            postRepository.addToLikeCount(row.postId(), -1);
        }
        return new LikeStateDto(false, postRepository.likeCountOf(row.postId()));
    }

    // ---------------------------------------------------------------- comments (T22a2)

    /** Oldest first. The owner and the moderators of the community also see the comments a block would hide (D-026). */
    @Transactional(readOnly = true)
    public CursorPageDto<CommentDto> comments(UserAccount viewer, UUID publicId, String after, Integer limit) {
        PostRow row = queries.one(viewer.getId(), publicId, true).orElseThrow(() -> new NotFoundException(POST_NOT_FOUND));
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        OpaqueCursor cursor = OpaqueCursor.decode(after, OpaqueCursor.COMMENT);
        if (cursor != null && cursor.order() == null) {
            throw new BadRequestException("Cursor no válido");
        }
        List<CommentRow> rows = queries.comments(row.postId(), viewer.getId(), row.staff(), cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<CommentRow> items = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasMore) {
            CommentRow last = items.get(items.size() - 1);
            next = OpaqueCursor.encode(OpaqueCursor.COMMENT, LocalDateTime.ofInstant(last.createdAt(), ZoneOffset.UTC), last.publicId());
        }
        return new CursorPageDto<>(items.stream().map(PostService::toDto).toList(), next);
    }

    /**
     * Members of the community comment (the owner and moderators included); a muted member and a non-member get 403.
     * On a profile post, whoever can see it. Notifies the author of the post after the commit (POST_COMMENT).
     */
    @Transactional
    public CommentDto comment(UserAccount user, UUID publicId, CreateCommentDto dto) {
        limiter.postComment(user.getId());
        String text = dto == null || dto.body() == null ? "" : dto.body().strip();
        if (text.isEmpty()) {
            throw new BadRequestException("Escribe algo para comentar");
        }
        if (text.length() > MAX_COMMENT) {
            throw new BadRequestException("El comentario no puede superar los " + MAX_COMMENT + " caracteres");
        }
        PostRow row = interactable(user, publicId);
        requireMember(row, user, true);
        PostComment saved = commentRepository.saveAndFlush(new PostComment(row.postId(), user.getId(), text));
        postRepository.addToCommentCount(row.postId(), 1);
        events.publishEvent(new PostCommented(row.publicId(), row.authorId(), user.getId(),
                text.length() > 80 ? text.substring(0, 80) + "…" : text));
        return toDto(queries.comment(saved.getPublicId(), user.getId(), false));
    }

    /** The author of the comment, or the owner or a moderator of the community of the post (D-026: also through blocks). */
    @Transactional
    public void deleteComment(UserAccount user, UUID publicId, UUID commentId) {
        PostRow row = queries.one(user.getId(), publicId, true).orElseThrow(() -> new NotFoundException(POST_NOT_FOUND));
        PostComment comment = commentRepository.findLive(commentId, row.postId())
                .orElseThrow(() -> new NotFoundException(COMMENT_NOT_FOUND));
        if (!comment.getAuthorId().equals(user.getId()) && !row.staff()) {
            // A comment hidden from her by a block answers like one that does not exist
            if (blockRepository.existsEitherWay(user.getId(), comment.getAuthorId())) {
                throw new NotFoundException(COMMENT_NOT_FOUND);
            }
            throw new ForbiddenException("No puedes borrar este comentario");
        }
        // Only the request that flips the row moves the counter
        if (commentRepository.markDeleted(comment.getId(), Instant.now()) == 0) {
            throw new NotFoundException(COMMENT_NOT_FOUND);
        }
        postRepository.addToCommentCount(row.postId(), -1);
    }

    /** The post as seen when interacting: a block in either direction hides it, also from the moderators (D-026 is for moderating). */
    private PostRow interactable(UserAccount user, UUID publicId) {
        return queries.one(user.getId(), publicId, false).orElseThrow(() -> new NotFoundException(POST_NOT_FOUND));
    }

    /** Community posts: only members interact; {@code writing} also rules out the muted. Profile posts: nothing more to check. */
    private void requireMember(PostRow row, UserAccount user, boolean writing) {
        if (row.communityId() == null) {
            return;
        }
        MemberStatus status = memberRepository.findStatus(row.communityId(), user.getId())
                .orElseThrow(() -> new ForbiddenException("Solo las miembros de la comunidad pueden participar"));
        if (status == MemberStatus.BANNED) {
            throw new NotFoundException(POST_NOT_FOUND);
        }
        if (writing && status == MemberStatus.MUTED) {
            throw new ForbiddenException("Estás silenciada en esta comunidad y no puedes comentar");
        }
    }

    private static CommentDto toDto(CommentRow r) {
        return new CommentDto(r.publicId(), r.body(), r.createdAt(),
                new PostDto.Author(r.authorUsername(), r.authorFullName(), r.authorPicture()), r.canDelete());
    }

    /** Public community: any user who is not banned; private: its members who are not banned. Else the 404 of a missing one. */
    private void requireVisible(Long communityId, Long userId) {
        Optional<MemberStatus> status = memberRepository.findStatus(communityId, userId);
        if (status.filter(s -> s == MemberStatus.BANNED).isPresent()
                || status.isEmpty() && !communityRepository.isPublic(communityId)) {
            throw new NotFoundException(COMMUNITY_NOT_FOUND);
        }
    }

    private CursorPageDto<PostDto> page(Scope scope, UserAccount viewer, Long scopeId, String before, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        OpaqueCursor cursor = OpaqueCursor.decode(before, OpaqueCursor.POST);
        if (cursor != null && cursor.order() == null) {
            throw new BadRequestException("Cursor no válido");
        }
        List<PostRow> rows = queries.page(scope, viewer.getId(), scopeId, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<PostRow> items = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasMore) {
            PostRow last = items.get(items.size() - 1);
            next = OpaqueCursor.encode(OpaqueCursor.POST, LocalDateTime.ofInstant(last.createdAt(), ZoneOffset.UTC), last.publicId());
        }
        return new CursorPageDto<>(assemble(items), next);
    }

    /** Adds the photos (one query for the whole page) and turns the rows into DTOs. */
    private List<PostDto> assemble(List<PostRow> rows) {
        Map<Long, List<MediaUrlsDto>> mediaByPost = new LinkedHashMap<>();
        for (MediaRow m : queries.media(rows.stream().map(PostRow::postId).toList())) {
            String prefix = MediaPurpose.POST_IMAGE.pathSegment() + "/" + m.mediaId() + "/";
            mediaByPost.computeIfAbsent(m.postId(), k -> new ArrayList<>()).add(new MediaUrlsDto(
                    storage.publicUrl(prefix + "thumb.jpg"), storage.publicUrl(prefix + "medium.jpg"),
                    storage.publicUrl(prefix + "large.jpg")));
        }
        return rows.stream().map(r -> new PostDto(
                r.publicId(), r.body(), r.createdAt(),
                new PostDto.Author(r.authorUsername(), r.authorFullName(), r.authorPicture()),
                r.communitySlug() == null ? null : new PostDto.Community(r.communitySlug(), r.communityName()),
                mediaByPost.getOrDefault(r.postId(), List.of()),
                r.likeCount(), r.commentCount(), r.likedByMe(), r.canDelete())).toList();
    }
}
