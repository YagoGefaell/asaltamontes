package io.github.yagogefaell.asaltamontes.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventDetailsDto;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.event.service.AttendeePromoted;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T15b: joining, waitlist and promotion.
 */
@RecordApplicationEvents
class EventAttendanceIT extends AbstractIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationEvents applicationEvents;

    @Test
    void manySimultaneousJoins_neverExceedThePlaces_andTheWaitlistHasNoGaps() throws Exception {
        UserAccount organizer = testData.createUser();
        long id = newEvent(organizer, 10, EventVisibility.PUBLIC, null).id();
        List<UserAccount> runners = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runners.add(testData.createUser());
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EventDetailsDto>> results = new ArrayList<>();
        for (UserAccount runner : runners) {
            results.add(pool.submit(() -> {
                start.await();
                return eventService.join(id, runner);
            }));
        }
        start.countDown();
        for (Future<EventDetailsDto> result : results) {
            result.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // The organizer has one of the 10 places
        assertThat(count(id, "GOING")).isEqualTo(10);
        assertThat(eventRepository.currentAttendeeCount(id)).isEqualTo(10);
        assertThat(count(id, "WAITLIST")).isEqualTo(91);
        List<Integer> positions = jdbcTemplate.queryForList(
                "SELECT waitlist_position FROM event_attendees WHERE event_id = ? AND status = 'WAITLIST' ORDER BY 1",
                Integer.class, id);
        assertThat(positions).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 91).boxed().toList());
    }

    @Test
    void joiningTwice_returnsTheCurrentStatus() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        long id = newEvent(organizer, 5, EventVisibility.PUBLIC, null).id();

        join(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.myStatus").value("GOING"));
        join(ana, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("GOING"))
                .andExpect(jsonPath("$.attendeeCount").value(2));
    }

    @Test
    void whenSomeoneLeaves_theFirstOnTheWaitlistGetsThePlace_andPositionsMoveUp() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount going = testData.createUser();
        UserAccount first = testData.createUser();
        UserAccount second = testData.createUser();
        UserAccount third = testData.createUser();
        long id = newEvent(organizer, 2, EventVisibility.PUBLIC, null).id();
        join(going, id).andExpect(jsonPath("$.myStatus").value("GOING"));
        join(first, id).andExpect(jsonPath("$.myStatus").value("WAITLIST"));
        join(second, id);
        join(third, id);

        leave(going, id).andExpect(status().isOk()).andExpect(jsonPath("$.myStatus").value("CANCELLED"));

        assertThat(statusOf(id, first)).isEqualTo("GOING");
        assertThat(positionOf(id, second)).isEqualTo(1);
        assertThat(positionOf(id, third)).isEqualTo(2);
        assertThat(eventRepository.currentAttendeeCount(id)).isEqualTo(2);
        assertThat(applicationEvents.stream(AttendeePromoted.class)
                .filter(e -> e.eventId() == id && e.userId().equals(first.getId()))).hasSize(1);

        // Leaving the waitlist closes the gap too
        leave(second, id);
        assertThat(positionOf(id, third)).isEqualTo(1);

        // And one can come back (to the end of the line)
        join(second, id).andExpect(jsonPath("$.myStatus").value("WAITLIST"));
        assertThat(positionOf(id, second)).isEqualTo(2);
    }

    @Test
    void moreCapacity_promotesFromTheWaitlist() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount first = testData.createUser();
        UserAccount second = testData.createUser();
        long id = newEvent(organizer, 1, EventVisibility.PUBLIC, null).id();
        join(first, id);
        join(second, id);

        mockMvc.perform(patch("/api/events/" + id).with(auth.bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeCount").value(2));

        assertThat(statusOf(id, first)).isEqualTo("GOING");
        assertThat(positionOf(id, second)).isEqualTo(1);
    }

    @Test
    void anEditLoadedBeforeSomeoneJoined_doesNotPutTheOldCountBack() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        long id = newEvent(organizer, 10, EventVisibility.PUBLIC, null).id();
        Event stale = eventRepository.findById(id).orElseThrow();

        eventService.join(id, ana);
        stale.setTitle("Título nuevo");
        eventRepository.save(stale);

        assertThat(eventRepository.currentAttendeeCount(id)).isEqualTo(2);
    }

    @Test
    void theOrganizerCannotLeave_andClosedOrHiddenEventsCannotBeJoined() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount outsider = testData.createUser();
        long id = newEvent(organizer, null, EventVisibility.PUBLIC, null).id();
        Community community = testData.createCommunity(organizer);
        long membersOnly = newEvent(organizer, null, EventVisibility.COMMUNITY, community.getId()).id();

        leave(organizer, id).andExpect(status().isConflict());
        join(outsider, membersOnly).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/events/" + id + "/cancel").with(auth.bearer(organizer))).andExpect(status().isOk());
        join(outsider, id).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("El evento está cancelado"));

        long started = newEvent(organizer, null, EventVisibility.PUBLIC, null).id();
        jdbcTemplate.update("UPDATE events SET starts_at = now() - interval '1 hour' WHERE id = ?", started);
        join(outsider, started).andExpect(status().isConflict());
    }

    @Test
    void attendees_goingInOrderOfArrival_waitlistByPosition() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount carla = testData.createUser();
        long id = newEvent(organizer, 2, EventVisibility.PUBLIC, null).id();
        join(ana, id);
        join(bea, id);
        join(carla, id);

        mockMvc.perform(get("/api/events/" + id + "/attendees").with(auth.bearer(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].username").value(organizer.getUsername()))
                .andExpect(jsonPath("$.content[1].username").value(ana.getUsername()));
        mockMvc.perform(get("/api/events/" + id + "/attendees").param("status", "WAITLIST").with(auth.bearer(carla)))
                .andExpect(jsonPath("$.content[0].username").value(bea.getUsername()))
                .andExpect(jsonPath("$.content[0].waitlistPosition").value(1))
                .andExpect(jsonPath("$.content[1].username").value(carla.getUsername()))
                .andExpect(jsonPath("$.content[1].waitlistPosition").value(2));
    }

    private EventDetailsDto newEvent(UserAccount organizer, Integer capacity, EventVisibility visibility, Long communityId) {
        return eventService.create(new CreateEventDto(
                testData.uniqueName("Quedada"), null, EventType.MEETUP, Instant.now().plus(3, ChronoUnit.DAYS), null,
                "Europe/Madrid", "Parking", null, null, "Vigo", null, null, null, capacity, visibility, communityId), organizer);
    }

    private ResultActions join(UserAccount user, long id) throws Exception {
        return mockMvc.perform(post("/api/events/" + id + "/join").with(auth.bearer(user)));
    }

    private ResultActions leave(UserAccount user, long id) throws Exception {
        return mockMvc.perform(post("/api/events/" + id + "/leave").with(auth.bearer(user)));
    }

    private int count(long id, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_attendees WHERE event_id = ? AND status = ?", Integer.class, id, status);
    }

    private String statusOf(long id, UserAccount user) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM event_attendees WHERE event_id = ? AND user_id = ?", String.class, id, user.getId());
    }

    private Integer positionOf(long id, UserAccount user) {
        return jdbcTemplate.queryForObject(
                "SELECT waitlist_position FROM event_attendees WHERE event_id = ? AND user_id = ?", Integer.class, id, user.getId());
    }

}
