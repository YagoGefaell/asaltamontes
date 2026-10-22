package io.github.yagogefaell.asaltamontes.event.service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
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
import io.github.yagogefaell.asaltamontes.event.dto.EventDetailsDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.dto.UpdateEventDto;
import io.github.yagogefaell.asaltamontes.event.repository.EventAttendeeRepository;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    public static final String DEFAULT_TIMEZONE = "Europe/Madrid";
    // Without an end time, an event counts as finished this long after it starts
    static final Duration DEFAULT_DURATION = Duration.ofHours(12);
    private static final int MAX_PAGE = 50;

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final ApplicationEventPublisher eventPublisher;

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
            community = communityRepository.findById(request.communityId())
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
        event.setTimezone(validTimezone(request.timezone()));
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
        return toDetails(event, organizer, AttendeeStatus.GOING);
    }

    /** 404 also when the user may not see it (COMMUNITY events for non-members), so it cannot be guessed. */
    @Transactional(readOnly = true)
    public EventDetailsDto get(Long eventId, UserAccount user) {
        Event event = findVisible(eventId, user);
        return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDto> listUpcoming(UserAccount user, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE));
        return eventRepository.findUpcomingVisibleTo(user.getId(), Instant.now(), page);
    }

    @Transactional
    public EventDetailsDto update(Long eventId, UpdateEventDto changes, UserAccount user) {
        Event event = findEditable(eventId, user);

        Instant startsAt = changes.startsAt() != null ? changes.startsAt() : event.getStartsAt();
        Instant endsAt = changes.endsAt() != null ? changes.endsAt() : event.getEndsAt();
        if (changes.startsAt() != null && !startsAt.isAfter(Instant.now())) {
            throw new BadRequestException("El evento tiene que empezar en el futuro");
        }
        validateEnd(startsAt, endsAt);
        Double latitude = changes.latitude() != null ? changes.latitude() : event.getLatitude();
        Double longitude = changes.longitude() != null ? changes.longitude() : event.getLongitude();
        validateCoordinates(latitude, longitude);
        if (changes.capacity() != null && changes.capacity() < event.getAttendeeCount()) {
            throw new ConflictException("Ya hay " + event.getAttendeeCount() + " personas apuntadas: no puedes dejar menos plazas");
        }

        if (changes.title() != null) event.setTitle(changes.title().strip());
        if (changes.description() != null) event.setDescription(changes.description());
        if (changes.type() != null) event.setType(changes.type());
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        if (changes.timezone() != null) event.setTimezone(validTimezone(changes.timezone()));
        if (changes.meetingPointName() != null) event.setMeetingPointName(changes.meetingPointName());
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        if (changes.city() != null) event.setCity(changes.city());
        if (changes.distanceKm() != null) event.setDistanceKm(changes.distanceKm());
        if (changes.elevationGainM() != null) event.setElevationGainM(changes.elevationGainM());
        if (changes.level() != null) event.setLevel(changes.level());
        if (changes.capacity() != null) event.setCapacity(changes.capacity());

        // Flush here so a concurrent edit fails as a 409 inside this request (@Version)
        eventRepository.saveAndFlush(event);
        return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
    }

    /** Idempotent: cancelling a cancelled event returns it as it is. */
    @Transactional
    public EventDetailsDto cancel(Long eventId, String reason, UserAccount user) {
        Event event = findVisible(eventId, user);
        if (!canEdit(event, user)) {
            throw new ForbiddenException("Solo la organizadora o la moderación de la comunidad pueden cancelar el evento");
        }
        if (event.getStatus() != EventStatus.CANCELLED) {
            event.setStatus(EventStatus.CANCELLED);
            event.setCancelledAt(Instant.now());
            event.setCancelReason(reason == null || reason.isBlank() ? null : reason.strip());
            eventRepository.saveAndFlush(event);
            eventPublisher.publishEvent(new EventCancelled(event.getId(), event.getCancelReason()));
        }
        return toDetails(event, user, attendeeRepository.findStatus(eventId, user.getId()).orElse(null));
    }

    private Event findVisible(Long eventId, UserAccount user) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado"));
        if (!canView(event, user)) {
            throw new NotFoundException("Evento no encontrado");
        }
        return event;
    }

    private Event findEditable(Long eventId, UserAccount user) {
        Event event = findVisible(eventId, user);
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

    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone.strip()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Zona horaria no válida");
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
            event.getId(),
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
            community != null ? community.getId() : null,
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
