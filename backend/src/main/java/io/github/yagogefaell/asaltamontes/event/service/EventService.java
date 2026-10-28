package io.github.yagogefaell.asaltamontes.event.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.domain.EventAttendee;
import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventAttendeeDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventDetailsDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.dto.UpdateEventDto;
import io.github.yagogefaell.asaltamontes.event.repository.EventAttendeeRepository;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.message.service.EventChatService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final List<String> REQUIRED_FIELDS = List.of("title", "type", "startsAt", "timezone");
    // Without an end time, an event counts as finished this long after it starts
    public static final Duration DEFAULT_DURATION = Duration.ofHours(12);
    private static final int MAX_PAGE = 50;
    private static final int Q_MIN = 2;
    private static final int Q_MAX = 80;

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final UserActionLimiter limiter;
    private final BlockService blockService;
    private final EventChatService eventChat;

    /** Creates the event; the organizer is its first attendee (GOING). */
    @Transactional
    public EventDetailsDto create(CreateEventDto request, UserAccount organizer) {
        Instant now = Instant.now();
        if (!request.startsAt().isAfter(now)) {
            throw new BadRequestException("El evento tiene que empezar en el futuro");
        }
        validateEnd(request.startsAt(), request.endsAt());
        validateCoordinates(request.latitude(), request.longitude());

        EventVisibility visibility = request.visibility() == null ? EventVisibility.PUBLIC : request.visibility();
        Community community = null;
        if (request.communityId() != null) {
            community = communityRepository.findByPublicId(request.communityId())
                    .orElseThrow(() -> new NotFoundException("Comunidad no encontrada"));
            if (!isCommunityStaff(community.getId(), organizer.getId())) {
                throw new ForbiddenException("Solo la propietaria o la moderación pueden crear eventos de la comunidad");
            }
        } else if (visibility == EventVisibility.COMMUNITY) {
            throw new BadRequestException("Un evento solo para miembros necesita una comunidad");
        }

        Event event = new Event();
        event.setTitle(request.title().strip());
        event.setDescription(request.description());
        event.setType(request.type());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setTimezone(EventTimezones.require(request.timezone()));
        event.setMeetingPointName(request.meetingPointName());
        event.setLatitude(request.latitude());
        event.setLongitude(request.longitude());
        event.setCity(request.city());
        event.setDistanceKm(request.distanceKm());
        event.setElevationGainM(request.elevationGainM());
        event.setLevel(request.level());
        event.setCapacity(request.capacity());
        event.setVisibility(visibility);
        event.setCommunity(community);
        // The principal comes from another persistence context: use a managed reference (lazy profile, T15a)
        event.setOrganizer(userAccountRepository.getReferenceById(organizer.getId()));
        event.setAttendeeCount(1);
        event = eventRepository.save(event);

        attendeeRepository.save(new EventAttendee(event, event.getOrganizer(), AttendeeStatus.GOING));
        eventChat.ensureChat(event.getId());
        return toDetails(event, organizer, AttendeeStatus.GOING);
    }

    /** 404 also when the user may not see it (COMMUNITY events for non-members), so it cannot be guessed. */
    @Transactional(readOnly = true)
    public EventDetailsDto get(UUID eventPublicId, UserAccount user) {
        Event event = findVisible(eventPublicId, user);
        return toDetails(event, user, attendeeRepository.findStatus(event.getId(), user.getId()).orElse(null));
    }

    /** The event as an iCalendar file (T21); 404 also when the user may not see it, as in {@link #get}. */
    @Transactional(readOnly = true)
    public String calendar(UUID eventPublicId, UserAccount user) {
        return EventCalendar.ics(findVisible(eventPublicId, user), Instant.now());
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDto> search(UserAccount user, EventFilter filter, Pageable pageable) {
        validate(filter);
        Instant from = filter.from() != null ? filter.from() : Instant.now();
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE));
        return eventRepository.search(user.getId(), from, filter, page);
    }

    /**
     * Upcoming events another user organizes that the caller can see (D-023): the usual visibility rules, in the
     * query. Never her attendance. Unknown or blocked in either direction: the same 404 (D-022).
     */
    @Transactional(readOnly = true)
    public Page<EventSummaryDto> searchByOrganizer(String username, UserAccount user, Instant from, Instant to,
            Pageable pageable) {
        Long organizerId = userAccountRepository.findByUsernameIgnoreCase(username)
                .filter(u -> !blockService.isBlockedEitherWay(user.getId(), u.getId()))
                .orElseThrow(() -> new UserNotFoundException(username)).getId();
        EventFilter byOrganizer = new EventFilter(from, to, null, null, null, null, null, null, null, null, null, null,
                false, false, null, false, organizerId);
        return search(user, byOrganizer, pageable);
    }

    /** Upcoming events of a community, with the same visibility rules as the general listing. */
    @Transactional(readOnly = true)
    public Page<EventSummaryDto> searchByCommunity(String slug, UserAccount user, EventFilter filter, Pageable pageable) {
        UUID communityId = communityRepository.findCommunityBySlug(slug, user.getId())
                .orElseThrow(() -> new NotFoundException("Comunidad no encontrada")).id();
        EventFilter inCommunity = new EventFilter(filter.from(), filter.to(), filter.type(), filter.level(), communityId,
                filter.city(), filter.minDistance(), filter.maxDistance(), filter.minLat(), filter.maxLat(),
                filter.minLng(), filter.maxLng(), filter.organizerMe(), filter.attendingMe(), filter.q(),
                filter.followedByMe(), filter.organizerKey());
        return search(user, inCommunity, pageable);
    }

    private void validate(EventFilter f) {
        if (f.q() != null && (f.q().length() < Q_MIN || f.q().length() > Q_MAX)) {
            throw new BadRequestException("El texto de búsqueda debe tener entre " + Q_MIN + " y " + Q_MAX + " caracteres");
        }
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BadRequestException("'to' no puede ser anterior a 'from'");
        }
        if (f.minDistance() != null && f.maxDistance() != null && f.maxDistance().compareTo(f.minDistance()) < 0) {
            throw new BadRequestException("La distancia máxima no puede ser menor que la mínima");
        }
        Double[] box = {f.minLat(), f.maxLat(), f.minLng(), f.maxLng()};
        long given = Arrays.stream(box).filter(Objects::nonNull).count();
        if (given == 0) {
            return;
        }
        if (given < 4) {
            throw new BadRequestException("El área necesita minLat, maxLat, minLng y maxLng");
        }
        if (f.minLat() < -90 || f.maxLat() > 90 || f.minLng() < -180 || f.maxLng() > 180
                || f.minLat() > f.maxLat() || f.minLng() > f.maxLng()) {
            throw new BadRequestException("El área no es válida");
        }
    }

    /**
     * PATCH semantics (D-020): a field absent from the body stays as it is, an explicit null clears it when it is
     * optional and is a 400 when it is required (title, type, startsAt, timezone).
     */
    @Transactional
    public EventDetailsDto update(UUID eventPublicId, UpdateEventDto changes, UserAccount user) {
        limiter.eventEdit(user.getId());
        for (String required : REQUIRED_FIELDS) {
            if (changes.has(required) && valueOf(changes, required) == null) {
                throw new BadRequestException("El campo " + required + " no puede quedar vacío");
            }
        }
        Event event = findEditable(eventPublicId, user);
        Long eventId = event.getId();

        Instant startsAt = changes.has("startsAt") ? changes.startsAt() : event.getStartsAt();
        Instant endsAt = changes.has("endsAt") ? changes.endsAt() : event.getEndsAt();
        if (changes.has("startsAt") && !startsAt.isAfter(Instant.now())) {
            throw new BadRequestException("El evento tiene que empezar en el futuro");
        }
        validateEnd(startsAt, endsAt);
        Double latitude = changes.has("latitude") ? changes.latitude() : event.getLatitude();
        Double longitude = changes.has("longitude") ? changes.longitude() : event.getLongitude();
        validateCoordinates(latitude, longitude);
        String timezone = changes.has("timezone") ? EventTimezones.require(changes.timezone()) : event.getTimezone();
        boolean capacityChanges = changes.has("capacity");
        if (capacityChanges) {
            // Same order as join/leave, and with the current number of attendees
            lockAndRefresh(event);
        }
        Integer previousCapacity = event.getCapacity();
        if (capacityChanges && changes.capacity() != null && changes.capacity() < event.getAttendeeCount()) {
            throw new ConflictException("Ya hay " + event.getAttendeeCount() + " personas apuntadas: no puedes dejar menos plazas");
        }

        List<String> changedFields = changedNotifiableFields(event, startsAt, timezone, changes);

        if (changes.has("title")) event.setTitle(changes.title().strip());
        if (changes.has("description")) event.setDescription(changes.description());
        if (changes.has("type")) event.setType(changes.type());
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setTimezone(timezone);
        if (changes.has("meetingPointName")) event.setMeetingPointName(changes.meetingPointName());
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        if (changes.has("city")) event.setCity(changes.city());
        if (changes.has("distanceKm")) event.setDistanceKm(changes.distanceKm());
        if (changes.has("elevationGainM")) event.setElevationGainM(changes.elevationGainM());
        if (changes.has("level")) event.setLevel(changes.level());
        if (capacityChanges) event.setCapacity(changes.capacity());

        // Flush here so a concurrent edit fails as a 409 inside this request (@Version)
        eventRepository.saveAndFlush(event);
        if (!changedFields.isEmpty()) {
            eventPublisher.publishEvent(new EventUpdated(eventId, user.getId(), changedFields));
            eventChat.eventUpdated(event, user.getId(), changedFields);
        }
        if (changedFields.contains("startsAt")) {
            // The reminders of the new date are still to be sent
            eventRepository.resetReminders(eventId);
        }
        if (changes.has("title")) {
            eventChat.syncProfile(event);
        }
        // More places, or no limit any more: the waitlist moves up
        if (capacityChanges && previousCapacity != null
                && (changes.capacity() == null || changes.capacity() > previousCapacity)) {
            promoteFromWaitlist(event);
            entityManager.refresh(event);
        }
        return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
    }

    /** What an attendee plans around (T18): when and where. Everything else changing does not notify anyone. */
    private static List<String> changedNotifiableFields(Event event, Instant startsAt, String timezone, UpdateEventDto changes) {
        List<String> changed = new java.util.ArrayList<>();
        if (!Objects.equals(event.getStartsAt(), startsAt)) changed.add("startsAt");
        if (!Objects.equals(event.getTimezone(), timezone)) changed.add("timezone");
        if (changes.has("city") && !Objects.equals(event.getCity(), changes.city())) changed.add("city");
        boolean meetingPointChanged = (changes.has("meetingPointName") && !Objects.equals(event.getMeetingPointName(), changes.meetingPointName()))
                || (changes.has("latitude") && !Objects.equals(event.getLatitude(), changes.latitude()))
                || (changes.has("longitude") && !Objects.equals(event.getLongitude(), changes.longitude()));
        if (meetingPointChanged) changed.add("meetingPoint");
        return changed;
    }

    private static Object valueOf(UpdateEventDto changes, String field) {
        return switch (field) {
            case "title" -> changes.title();
            case "type" -> changes.type();
            case "startsAt" -> changes.startsAt();
            default -> changes.timezone();
        };
    }

    /** Idempotent: cancelling a cancelled event returns it as it is. */
    @Transactional
    public EventDetailsDto cancel(UUID eventPublicId, String reason, UserAccount user) {
        Event event = findVisible(eventPublicId, user);
        Long eventId = event.getId();
        if (!canEdit(event, user)) {
            throw new ForbiddenException("Solo la organizadora o la moderación de la comunidad pueden cancelar el evento");
        }
        if (event.getStatus() != EventStatus.CANCELLED) {
            event.setStatus(EventStatus.CANCELLED);
            event.setCancelledAt(Instant.now());
            event.setCancelReason(reason == null || reason.isBlank() ? null : reason.strip());
            eventRepository.saveAndFlush(event);
            eventPublisher.publishEvent(new EventCancelled(event.getId(), event.getCancelReason(), user.getId()));
            eventChat.eventCancelled(event, user.getId());
        }
        return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
    }

    /**
     * Joins the event (T15b): GOING if there is a free place, otherwise WAITLIST at the end. Idempotent: joining
     * again returns the current status. Everything runs with the event row locked, so places are never exceeded
     * and waitlist positions stay 1..n without gaps, however many people join at once.
     */
    @Transactional
    public EventDetailsDto join(UUID eventPublicId, UserAccount user) {
        Event event = findVisible(eventPublicId, user);
        Long eventId = event.getId();
        lockAndRefresh(event);
        requireOpen(event);

        Optional<EventAttendee> current = attendeeRepository.findByEventIdAndUserId(eventId, user.getId());
        if (current.isPresent() && current.get().getStatus() != AttendeeStatus.CANCELLED) {
            return toDetails(event, user, current.get().getStatus());
        }

        EventAttendee attendee = current.orElseGet(() ->
                new EventAttendee(event, userAccountRepository.getReferenceById(user.getId()), AttendeeStatus.GOING));
        if (hasFreePlace(event)) {
            attendee.setStatus(AttendeeStatus.GOING);
            attendee.setWaitlistPosition(null);
            eventRepository.addToAttendeeCount(eventId, 1);
        } else {
            attendee.setStatus(AttendeeStatus.WAITLIST);
            attendee.setWaitlistPosition(attendeeRepository.maxWaitlistPosition(eventId, AttendeeStatus.WAITLIST) + 1);
        }
        attendeeRepository.saveAndFlush(attendee);
        eventPublisher.publishEvent(new EventAttendeeJoined(eventId, user.getId(), attendee.getStatus() == AttendeeStatus.WAITLIST));
        if (attendee.getStatus() == AttendeeStatus.GOING) {
            eventChat.addAttendee(eventId, user.getId());
        }

        entityManager.refresh(event);
        return toDetails(event, user, attendee.getStatus());
    }

    /**
     * Leaves the event or the waitlist. A free place goes to the first one on the waitlist. Idempotent.
     * The organizer cannot leave her own event: she cancels it.
     */
    @Transactional
    public EventDetailsDto leave(UUID eventPublicId, UserAccount user) {
        Event event = findVisible(eventPublicId, user);
        Long eventId = event.getId();
        lockAndRefresh(event);

        Optional<EventAttendee> current = attendeeRepository.findByEventIdAndUserId(eventId, user.getId())
                .filter(attendee -> attendee.getStatus() != AttendeeStatus.CANCELLED);
        if (current.isEmpty()) {
            return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
        }
        if (event.getOrganizer().getId().equals(user.getId())) {
            throw new ConflictException("La organizadora no puede salir de su evento; si no se va a hacer, cancélalo");
        }

        cancelAttendance(event, current.get(), "LEFT");

        entityManager.refresh(event);
        return toDetails(event, user, AttendeeStatus.CANCELLED);
    }

    /**
     * She loses her place in every members-only event of the community that has not finished (BUG-074): expelled or
     * out by her own will she must not stay as an attendee, in the chat or in the reminders. The places go to the
     * waitlist as in {@link #leave}. Events she organizes stay as they are. Runs in the caller's transaction; nothing
     * re-enrols her if she is readmitted (she joins again by hand).
     */
    @Transactional
    public void withdrawFromCommunityEvents(Long communityId, Long userId, String reason) {
        for (Long eventId : attendeeRepository.findOpenCommunityEventIds(communityId, userId)) {
            eventRepository.lockById(eventId);
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                continue;
            }
            entityManager.refresh(event);
            EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                    .filter(a -> a.getStatus() != AttendeeStatus.CANCELLED).orElse(null);
            if (attendee != null && statusAt(event, Instant.now()) == EventStatus.SCHEDULED) {
                cancelAttendance(event, attendee, reason);
            }
        }
    }

    // Cancels her attendance (GOING or WAITLIST), frees the place and promotes the waitlist. The caller holds the event lock.
    private void cancelAttendance(Event event, EventAttendee attendee, String chatReason) {
        Long eventId = event.getId();
        AttendeeStatus previous = attendee.getStatus();
        Integer position = attendee.getWaitlistPosition();
        attendee.setStatus(AttendeeStatus.CANCELLED);
        attendee.setWaitlistPosition(null);
        attendeeRepository.saveAndFlush(attendee);

        if (previous == AttendeeStatus.WAITLIST) {
            attendeeRepository.shiftWaitlistAfter(eventId, position);
        } else {
            eventRepository.addToAttendeeCount(eventId, -1);
            eventChat.removeAttendee(eventId, attendee.getUser().getId(), chatReason);
            entityManager.refresh(event);
            if (event.getStatus() == EventStatus.SCHEDULED) {
                promoteFromWaitlist(event);
            }
        }
    }

    /** Attendees by status (GOING by default): GOING in order of arrival, WAITLIST by position. Blocks hide each other (BUG-069). */
    @Transactional(readOnly = true)
    public Page<EventAttendeeDto> getAttendees(UUID eventPublicId, AttendeeStatus status, UserAccount user, Pageable pageable) {
        Long eventId = findVisible(eventPublicId, user).getId();
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE));
        return attendeeRepository.findAttendees(eventId, status == null ? AttendeeStatus.GOING : status, user.getId(), page);
    }

    // Fills the free places with the first ones on the waitlist. The caller holds the lock of the event.
    private void promoteFromWaitlist(Event event) {
        while (hasFreePlace(event)) {
            List<EventAttendee> next = attendeeRepository.findWaitlist(event.getId(), Limit.of(1));
            if (next.isEmpty()) {
                return;
            }
            EventAttendee promoted = next.get(0);
            int position = promoted.getWaitlistPosition();
            promoted.setStatus(AttendeeStatus.GOING);
            promoted.setWaitlistPosition(null);
            attendeeRepository.saveAndFlush(promoted);
            attendeeRepository.shiftWaitlistAfter(event.getId(), position);
            eventRepository.addToAttendeeCount(event.getId(), 1);
            entityManager.refresh(event);
            eventPublisher.publishEvent(new AttendeePromoted(event.getId(), promoted.getUser().getId()));
            eventChat.addAttendee(event.getId(), promoted.getUser().getId());
        }
    }

    private boolean hasFreePlace(Event event) {
        return event.getCapacity() == null || event.getAttendeeCount() < event.getCapacity();
    }

    private void lockAndRefresh(Event event) {
        eventRepository.lockById(event.getId());
        // What other transactions committed before we got the lock (places, capacity)
        entityManager.refresh(event);
    }

    private static void requireOpen(Event event) {
        EventStatus status = statusAt(event, Instant.now());
        if (status == EventStatus.CANCELLED) {
            throw new ConflictException("El evento está cancelado");
        }
        if (status == EventStatus.FINISHED || !event.getStartsAt().isAfter(Instant.now())) {
            throw new ConflictException("El evento ya ha empezado");
        }
    }

    private Event findVisible(UUID eventPublicId, UserAccount user) {
        Event event = eventRepository.findByPublicId(eventPublicId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado"));
        if (!canView(event, user)) {
            throw new NotFoundException("Evento no encontrado");
        }
        return event;
    }

    private Event findEditable(UUID eventPublicId, UserAccount user) {
        Event event = findVisible(eventPublicId, user);
        if (!canEdit(event, user)) {
            throw new ForbiddenException("Solo la organizadora o la moderación de la comunidad pueden editar el evento");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("No se puede editar un evento cancelado");
        }
        return event;
    }

    private boolean canView(Event event, UserAccount user) {
        if (event.getVisibility() == EventVisibility.PUBLIC || event.getOrganizer().getId().equals(user.getId())) {
            return true;
        }
        return communityMemberRepository.findByCommunityIdAndUserAccountId(event.getCommunity().getId(), user.getId())
                .filter(member -> member.getStatus() != MemberStatus.BANNED)
                .isPresent();
    }

    private boolean canEdit(Event event, UserAccount user) {
        return event.getOrganizer().getId().equals(user.getId())
                || (event.getCommunity() != null && isCommunityStaff(event.getCommunity().getId(), user.getId()));
    }

    private boolean isCommunityStaff(Long communityId, Long userId) {
        Optional<CommunityMember> member = communityMemberRepository.findByCommunityIdAndUserAccountId(communityId, userId);
        return member.filter(m -> m.getStatus() != MemberStatus.BANNED)
                .map(m -> m.getRole() == CommunityRole.OWNER || m.getRole() == CommunityRole.MODERATOR)
                .orElse(false);
    }

    private static void validateEnd(Instant startsAt, Instant endsAt) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("El evento tiene que terminar después de empezar");
        }
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Indica latitud y longitud, o ninguna de las dos");
        }
    }

    static EventStatus statusAt(Event event, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return EventStatus.CANCELLED;
        }
        Instant end = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt().plus(DEFAULT_DURATION);
        return end.isBefore(now) ? EventStatus.FINISHED : EventStatus.SCHEDULED;
    }

    private EventDetailsDto toDetails(Event event, UserAccount user, AttendeeStatus myStatus) {
        Community community = event.getCommunity();
        UserAccount organizer = event.getOrganizer();
        UserProfile profile = organizer.getProfile();
        return new EventDetailsDto(
            event.getPublicId(),
            event.getTitle(),
            event.getDescription(),
            event.getType(),
            statusAt(event, Instant.now()),
            event.getStartsAt(),
            event.getEndsAt(),
            event.getTimezone(),
            event.getMeetingPointName(),
            event.getLatitude(),
            event.getLongitude(),
            event.getCity(),
            event.getDistanceKm(),
            event.getElevationGainM(),
            event.getLevel(),
            event.getCapacity(),
            event.getAttendeeCount(),
            event.getVisibility(),
            community != null ? community.getPublicId() : null,
            community != null ? community.getSlug() : null,
            community != null ? community.getName() : null,
            organizer.getUsername(),
            profile != null ? profile.getFullName() : null,
            profile != null ? profile.getProfilePictureUrl() : null,
            event.getImageUrl(),
            event.getCancelReason(),
            event.getCancelledAt(),
            myStatus,
            canEdit(event, user)
        );
    }
}
