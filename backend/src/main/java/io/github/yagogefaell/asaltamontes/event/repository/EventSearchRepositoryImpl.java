package io.github.yagogefaell.asaltamontes.event.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.domain.EventAttendee;
import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.EventAttendeePreviewDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.user.domain.UserBlock;
import io.github.yagogefaell.asaltamontes.user.domain.UserFollow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Built with the Criteria API: only the filters that were given end up in the SQL, so each one can use its
 * index (a "param IS NULL OR ..." query cannot, and PostgreSQL cannot even infer the type of those params).
 */
public class EventSearchRepositoryImpl implements EventSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<EventSummaryDto> search(Long userId, Instant from, EventFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<EventSummaryDto> query = cb.createQuery(EventSummaryDto.class);
        Root<Event> e = query.from(Event.class);
        Join<Object, Object> organizer = e.join("organizer");
        Join<Object, Object> community = e.join("community", JoinType.LEFT);
        query.select(cb.construct(EventSummaryDto.class,
                e.get("publicId"), e.get("title"), e.get("type"), e.get("startsAt"), e.get("timezone"),
                e.get("meetingPointName"), e.get("city"), e.get("distanceKm"), e.get("elevationGainM"),
                e.get("level"), e.get("capacity"), e.get("attendeeCount"),
                community.get("slug"), community.get("name"), organizer.get("username"), e.get("imageUrl"),
                e.get("latitude"), e.get("longitude")));
        query.where(predicates(cb, query, e, community, userId, from, filter).toArray(Predicate[]::new));
        query.orderBy(cb.asc(e.get("startsAt")), cb.asc(e.get("id")));
        TypedQuery<EventSummaryDto> typed = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        List<EventSummaryDto> content = withAttendeePreviews(typed.getResultList(), userId);

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<Event> ce = count.from(Event.class);
            Join<Object, Object> cc = ce.join("community", JoinType.LEFT);
            count.select(cb.count(ce));
            count.where(predicates(cb, count, ce, cc, userId, from, filter).toArray(Predicate[]::new));
            return entityManager.createQuery(count).getSingleResult();
        });
    }

    private static final String ACCENTED = "áàäâãåéèëêíìïîóòöôõúùüûñç";
    private static final String PLAIN = "aaaaaaeeeeiiiiooooouuuunc";

    // lower + translate stands in for unaccent, which would need an extension in the database
    private static Expression<String> folded(CriteriaBuilder cb, Expression<String> text) {
        return cb.function("translate", String.class, cb.lower(text), cb.literal(ACCENTED), cb.literal(PLAIN));
    }

    /** Same folding as {@link #folded} for the text typed by her. */
    static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            int i = ACCENTED.indexOf(c);
            out.append(i >= 0 ? PLAIN.charAt(i) : c);
        }
        return out.toString();
    }

    /**
     * Up to 3 people going per event of the page, in ONE extra query whatever the page size (no N+1). Blocked
     * users in either direction are left out (D-022).
     */
    @SuppressWarnings("unchecked")
    private List<EventSummaryDto> withAttendeePreviews(List<EventSummaryDto> page, Long userId) {
        if (page.isEmpty()) {
            return page;
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT t.event_public_id, t.username, t.full_name, t.profile_picture_url, t.avatar_media_id
                FROM (
                    SELECT e.public_id AS event_public_id, u.username, p.full_name, p.profile_picture_url,
                           p.avatar_media_id,
                           row_number() OVER (PARTITION BY a.event_id ORDER BY a.joined_at, a.id) AS rn
                    FROM event_attendees a
                    JOIN events e ON e.id = a.event_id
                    JOIN users u ON u.id = a.user_id
                    LEFT JOIN user_profiles p ON p.user_id = u.id
                    WHERE e.public_id IN (:ids) AND a.status = 'GOING'
                      AND (u.id = :me OR NOT EXISTS (
                          SELECT 1 FROM user_blocks bl
                          WHERE (bl.blocker_id = :me AND bl.blocked_id = u.id)
                             OR (bl.blocked_id = :me AND bl.blocker_id = u.id)))
                ) t
                WHERE t.rn <= 3
                ORDER BY t.event_public_id, t.rn
                """)
                .setParameter("ids", page.stream().map(EventSummaryDto::id).toList())
                .setParameter("me", userId)
                .getResultList();
        Map<UUID, List<EventAttendeePreviewDto>> byEvent = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String picture = (String) r[3];
            String thumb = picture == null || picture.isBlank() ? null
                    : r[4] != null ? picture.replaceFirst("/medium\\.jpg$", "/thumb.jpg") : picture;
            byEvent.computeIfAbsent((UUID) r[0], k -> new ArrayList<>()).add(new EventAttendeePreviewDto(
                    (String) r[1], (String) r[2], thumb == null ? null : new EventAttendeePreviewDto.Avatar(thumb)));
        }
        return page.stream().map(s -> s.withAttendeePreview(byEvent.getOrDefault(s.id(), List.of()))).toList();
    }

    private List<Predicate> predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Event> e,
            Join<Object, Object> community, Long userId, Instant from, EventFilter f) {
        List<Predicate> where = new ArrayList<>();
        where.add(cb.greaterThanOrEqualTo(e.get("startsAt"), from));
        where.add(cb.equal(e.get("status"), EventStatus.SCHEDULED));

        // Visible: public, her own, or of a community she belongs to (not banned)
        Subquery<Integer> member = query.subquery(Integer.class);
        Root<CommunityMember> m = member.from(CommunityMember.class);
        member.select(cb.literal(1)).where(
                cb.equal(m.get("community"), community),
                cb.equal(m.get("userAccount").get("id"), userId),
                cb.notEqual(m.get("status"), MemberStatus.BANNED));
        where.add(cb.or(
                cb.equal(e.get("visibility"), EventVisibility.PUBLIC),
                cb.and(cb.equal(e.get("organizer").get("id"), userId), cb.isNull(e.get("community"))),
                cb.exists(member)));

        if (f.to() != null) {
            where.add(cb.lessThanOrEqualTo(e.get("startsAt"), f.to()));
        }
        if (f.type() != null) {
            where.add(cb.equal(e.get("type"), f.type()));
        }
        if (f.level() != null) {
            where.add(cb.equal(e.get("level"), f.level()));
        }
        if (f.communityId() != null) {
            where.add(cb.equal(community.get("publicId"), f.communityId()));
        }
        if (f.city() != null && !f.city().isBlank()) {
            where.add(cb.equal(cb.lower(e.get("city")), f.city().strip().toLowerCase()));
        }
        if (f.q() != null) {
            // Case and accent insensitive, % and _ typed by her are searched literally
            String pattern = "%" + fold(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.add(cb.or(
                    cb.like(folded(cb, e.get("title")), pattern, '\\'),
                    cb.like(folded(cb, e.get("city")), pattern, '\\'),
                    cb.like(folded(cb, e.get("meetingPointName")), pattern, '\\')));
        }
        if (f.minDistance() != null) {
            where.add(cb.greaterThanOrEqualTo(e.get("distanceKm"), f.minDistance()));
        }
        if (f.maxDistance() != null) {
            where.add(cb.lessThanOrEqualTo(e.get("distanceKm"), f.maxDistance()));
        }
        if (f.minLat() != null) { // the service guarantees the box is complete
            where.add(cb.between(e.get("latitude"), f.minLat(), f.maxLat()));
            where.add(cb.between(e.get("longitude"), f.minLng(), f.maxLng()));
        }
        if (f.organizerMe()) {
            where.add(cb.equal(e.get("organizer").get("id"), userId));
        }
        if (f.attendingMe()) {
            Subquery<Integer> attending = query.subquery(Integer.class);
            Root<EventAttendee> a = attending.from(EventAttendee.class);
            attending.select(cb.literal(1)).where(
                    cb.equal(a.get("event"), e),
                    cb.equal(a.get("user").get("id"), userId),
                    a.get("status").in(AttendeeStatus.GOING, AttendeeStatus.WAITLIST));
            where.add(cb.exists(attending));
        }
        if (f.organizerKey() != null) {
            where.add(cb.equal(e.get("organizer").get("id"), f.organizerKey()));
        }
        if (f.followedByMe()) {
            Subquery<Integer> followed = query.subquery(Integer.class);
            Root<UserFollow> follow = followed.from(UserFollow.class);
            followed.select(cb.literal(1)).where(
                    cb.equal(follow.get("follower").get("id"), userId),
                    cb.equal(follow.get("following"), e.get("organizer")));
            where.add(cb.exists(followed));
            // A block removes the follow, but the listing must not depend on that
            Subquery<Integer> blocked = query.subquery(Integer.class);
            Root<UserBlock> block = blocked.from(UserBlock.class);
            blocked.select(cb.literal(1)).where(cb.or(
                    cb.and(cb.equal(block.get("blocker").get("id"), userId),
                            cb.equal(block.get("blocked"), e.get("organizer"))),
                    cb.and(cb.equal(block.get("blocked").get("id"), userId),
                            cb.equal(block.get("blocker"), e.get("organizer")))));
            where.add(cb.not(cb.exists(blocked)));
        }
        return where;
    }
}
