package io.github.yagogefaell.asaltamontes.community.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.pagination.SortWhitelist;
import io.github.yagogefaell.asaltamontes.community.dto.MyCommunityDto;
import io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityJoinRequestRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository.MyCommunityRow;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository.NextEventRow;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import lombok.RequiredArgsConstructor;

/** The "Comunidades" tab (T29i/T31c): her communities with chat and next meetup, and what is waiting for her. */
@Service
@RequiredArgsConstructor
public class MyCommunitiesService {

    private static final int MAX_PAGE = 50;
    /** Pending lists are not paginated: nobody moderates or requests more than this many at once. */
    static final int PENDING_LIMIT = 50;
    // Last message of the chat, or the day she joined while nobody has written
    private static final String ACTIVITY = "COALESCE(ch.lastMessageAt, m.joinedAt)";
    private static final SortWhitelist SORT = new SortWhitelist(
            Map.of("name", "c.name", "memberCount", "c.memberCount", "createdAt", "c.createdAt", "activity", ACTIVITY),
            "activity");

    private final CommunityMemberRepository memberRepository;
    private final CommunityJoinRequestRepository joinRequestRepository;
    private final EventRepository eventRepository;
    private final BlockService blockService;

    /** Three statements whatever the page size (plus the count): the page, the blocks among senders, the next events. */
    @Transactional(readOnly = true)
    public Page<MyCommunityDto> myCommunities(UserAccount me, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE);
        Pageable page = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), size,
                        JpaSort.unsafe(Sort.Direction.DESC, ACTIVITY).and(JpaSort.unsafe(Sort.Direction.ASC, "c.id")))
                : SORT.apply(PageRequest.of(pageable.getPageNumber(), size, pageable.getSort()));
        Page<MyCommunityRow> rows = memberRepository.findMyCommunities(me.getId(), page);

        Set<Long> blockedSenders = blockService.blockedEitherWayAmong(me.getId(), rows.stream()
                .map(MyCommunityRow::lastSenderUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, NextEventRow> nextEvents = new HashMap<>();
        if (rows.hasContent()) {
            eventRepository.findNextEventsOf(
                    rows.stream().map(MyCommunityRow::communityInternalId).toList(), Instant.now())
                    .forEach(e -> nextEvents.putIfAbsent(e.communityInternalId(), e));
        }
        return rows.map(r -> toDto(r, blockedSenders, nextEvents.get(r.communityInternalId())));
    }

    private static MyCommunityDto toDto(MyCommunityRow r, Set<Long> blockedSenders, NextEventRow next) {
        boolean hidden = r.lastSenderUserId() != null && blockedSenders.contains(r.lastSenderUserId());
        var chat = new MyCommunityDto.Chat(r.conversationId(), hidden ? null : r.lastMessage(),
                hidden ? null : r.lastSenderUsername(), r.lastMessageAt(), r.unreadCount() == null ? 0 : r.unreadCount());
        var nextEvent = next == null ? null
                : new MyCommunityDto.NextEvent(next.id(), next.title(), next.startsAt(), next.timezone());
        return new MyCommunityDto(r.id(), r.name(), r.slug(), r.imageUrl(), r.role(), r.memberCount(), r.visibility(),
                r.city(), r.province(), chat, nextEvent);
    }

    @Transactional(readOnly = true)
    public PendingCommunitiesDto pending(UserAccount me) {
        return new PendingCommunitiesDto(
                joinRequestRepository.findToApprove(me.getId(), Limit.of(PENDING_LIMIT)),
                joinRequestRepository.findMine(me.getId(), Limit.of(PENDING_LIMIT)));
    }
}
