package io.github.yagogefaell.asaltamontes.event.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
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
        List<EventSummaryDto> content = typed.getResultList();

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<Event> ce = count.from(Event.class);
            Join<Object, Object> cc = ce.join("community", JoinType.LEFT);
            count.select(cb.count(ce));
            count.where(predicates(cb, count, ce, cc, userId, from, filter).toArray(Predicate[]::new));
            return entityManager.createQuery(count).getSingleResult();
        });
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
                cb.equal(e.get("organizer").get("id"), userId),
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
        return where;
    }
}
