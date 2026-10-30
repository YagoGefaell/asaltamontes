package io.github.yagogefaell.asaltamontes.report.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.domain.MessageType;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.post.domain.Post;
import io.github.yagogefaell.asaltamontes.post.domain.PostComment;
import io.github.yagogefaell.asaltamontes.post.repository.PostCommentRepository;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository;
import io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository.PostRow;
import io.github.yagogefaell.asaltamontes.post.repository.PostRepository;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

/**
 * Turns "type + reference" into the reported thing, applying the same visibility the reporter has when she looks at
 * it: whatever she cannot see answers the same 404 as what does not exist (no probing of private content through
 * the report form). Reporting yourself is a 400. Reading only; the report is inserted by {@link ReportService}.
 */
@Component
@RequiredArgsConstructor
public class ReportTargetResolver {

    private static final String NOT_FOUND = "No encontramos lo que quieres denunciar";

    private final UserAccountRepository userRepository;
    private final PostQueryRepository postQueries;
    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final EventService eventService;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final BlockService blockService;

    @Transactional(readOnly = true)
    public ResolvedTarget resolve(UserAccount reporter, ReportTargetType type, String target) {
        return switch (type) {
            case USER -> user(reporter, target.strip());
            case POST -> post(reporter, uuid(target));
            case COMMENT -> comment(reporter, uuid(target));
            case MESSAGE -> message(reporter, uuid(target));
            case EVENT -> event(reporter, uuid(target));
            case COMMUNITY -> community(reporter, target.strip());
        };
    }

    private ResolvedTarget user(UserAccount reporter, String username) {
        UserAccount reported = userRepository.findByUsernameIgnoreCase(username).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        // Whoever blocked her does not exist for her (as in her profile): no oracle on who blocked whom
        if (blockService.hasBlocked(reported.getId(), reporter.getId())) {
            throw new NotFoundException(NOT_FOUND);
        }
        notYourself(reporter, reported.getId());
        return new ResolvedTarget(reported.getId(), null, reported.getId(), null);
    }

    private ResolvedTarget post(UserAccount reporter, UUID publicId) {
        // moderating = true: the moderators of a community also report what a block would hide from them
        PostRow row = postQueries.one(reporter.getId(), publicId, true).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        notYourself(reporter, row.authorId());
        return new ResolvedTarget(row.postId(), row.publicId(), row.authorId(), row.body());
    }

    private ResolvedTarget comment(UserAccount reporter, UUID publicId) {
        PostComment comment = commentRepository.findLiveByPublicId(publicId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        Post post = postRepository.findById(comment.getPostId()).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        // She must be able to see the post the comment hangs from
        PostRow postRow = postQueries.one(reporter.getId(), post.getPublicId(), true).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        // A comment a block hides from her is hidden here too (the community staff see it, D-026)
        if (!postRow.staff() && blockService.isBlockedEitherWay(reporter.getId(), comment.getAuthorId())) {
            throw new NotFoundException(NOT_FOUND);
        }
        notYourself(reporter, comment.getAuthorId());
        return new ResolvedTarget(comment.getId(), comment.getPublicId(), comment.getAuthorId(), comment.getBody());
    }

    private ResolvedTarget message(UserAccount reporter, UUID publicId) {
        Message message = messageRepository.findByPublicId(publicId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        // Only a participant who can read it; in a group, nothing sent before she joined (the same 404 for all)
        LocalDateTime historyStart;
        try {
            historyStart = messageService.historyStart(message.getConversation().getId(), reporter.getId());
        } catch (NotFoundException e) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (message.getSentAt().isBefore(historyStart)) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (message.getType() != MessageType.USER || message.getSender() == null) {
            throw new BadRequestException("Los mensajes del sistema no se pueden denunciar");
        }
        notYourself(reporter, message.getSender().getId());
        // The original may be deleted later: the text is kept with the report
        return new ResolvedTarget(message.getId(), message.getPublicId(), message.getSender().getId(), message.getContent());
    }

    private ResolvedTarget event(UserAccount reporter, UUID publicId) {
        Event event;
        try {
            event = eventService.findVisible(publicId, reporter);
        } catch (NotFoundException e) {
            throw new NotFoundException(NOT_FOUND);
        }
        Long organizerId = event.getOrganizer().getId();
        notYourself(reporter, organizerId);
        return new ResolvedTarget(event.getId(), event.getPublicId(), organizerId, null);
    }

    private ResolvedTarget community(UserAccount reporter, String slug) {
        Long communityId = communityRepository.findIdBySlug(slug).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        Optional<MemberStatus> status = memberRepository.findStatus(communityId, reporter.getId());
        if (status.filter(s -> s == MemberStatus.BANNED).isPresent()
                || status.isEmpty() && !communityRepository.isPublic(communityId)) {
            throw new NotFoundException(NOT_FOUND);
        }
        Community community = communityRepository.findById(communityId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return new ResolvedTarget(communityId, community.getPublicId(), null, null);
    }

    private static void notYourself(UserAccount reporter, Long ownerId) {
        if (reporter.getId().equals(ownerId)) {
            throw new BadRequestException("No puedes denunciar tu propio contenido");
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            // Same answer as an unknown id
            throw new NotFoundException(NOT_FOUND);
        }
    }
}
