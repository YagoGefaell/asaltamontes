package io.github.yagogefaell.asaltamontes.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.event.service.EventCancelled;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@RecordApplicationEvents
class EventControllerIT extends AbstractIntegrationTest {

    @Autowired private CommunityService communityService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private EventRepository eventRepository;

    private static final Instant NEXT_WEEK = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Test
    void create_makesTheOrganizerTheFirstAttendee_andOthersSeeItWithoutRights() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        long id = idOf(create(ana, event("Subida al Galiñeiro"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.attendeeCount").value(1))
                .andExpect(jsonPath("$.myStatus").value("GOING"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.organizerUsername").value(ana.getUsername()))
                .andExpect(jsonPath("$.organizerFullName").value("Test User"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC")));

        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").doesNotExist())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.startsAt").value(NEXT_WEEK.toString()));
    }

    @Test
    void create_rejectsInvalidData() throws Exception {
        UserAccount ana = testData.createUser();

        create(ana, event("Antes de ayer", Map.of("startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El evento tiene que empezar en el futuro"));
        create(ana, event("Al revés", Map.of("endsAt", NEXT_WEEK.minus(1, ChronoUnit.HOURS).toString())))
                .andExpect(status().isBadRequest());
        create(ana, event("Media coordenada", Map.of("latitude", 42.2))).andExpect(status().isBadRequest());
        create(ana, event("Fuera del mapa", Map.of("latitude", 95.0, "longitude", 1.0))).andExpect(status().isBadRequest());
        create(ana, event("Zona rara", Map.of("timezone", "Marte/Olimpo"))).andExpect(status().isBadRequest());
        create(ana, event("Sin plazas", Map.of("capacity", 0))).andExpect(status().isBadRequest());
        create(ana, event(" ", Map.of())).andExpect(status().isBadRequest());
        create(ana, event("Solo socias", Map.of("visibility", "COMMUNITY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Un evento solo para miembros necesita una comunidad"));
        create(ana, event("Comunidad fantasma", Map.of("communityId", 999999999)))
                .andExpect(status().isNotFound());
    }

    @Test
    void communityEvents_onlyStaffCreateThem_andMembersOnlyEventsAreHiddenFromOthers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        UserAccount banned = testData.createUser();
        Community community = testData.createCommunity(owner);
        communityService.joinCommunity(community.getId(), member);
        communityService.joinCommunity(community.getId(), banned);
        jdbcTemplate.update("UPDATE community_members SET status = 'BANNED' WHERE user_account_id = ?", banned.getId());

        create(member, event("Quedada de socia", Map.of("communityId", community.getId())))
                .andExpect(status().isForbidden());

        String title = testData.uniqueName("Solo socias");
        long id = idOf(create(owner, event(title, Map.of("communityId", community.getId(), "visibility", "COMMUNITY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.communitySlug").value(community.getSlug())));

        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(member))).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Evento no encontrado"));
        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(banned))).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/events").param("size", "50").with(auth.bearer(member)))
                .andExpect(jsonPath("$.content[*].title").value(hasItem(title)));
        mockMvc.perform(get("/api/events").param("size", "50").with(auth.bearer(outsider)))
                .andExpect(jsonPath("$.content[*].title").value(not(hasItem(title))));
    }

    @Test
    void update_byTheOrganizerOrCommunityModeration_notByOthers_andNeverBelowTheAttendees() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount moderator = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        communityService.joinCommunity(community.getId(), moderator);
        jdbcTemplate.update("UPDATE community_members SET role = 'MODERATOR' WHERE user_account_id = ?", moderator.getId());

        long id = idOf(create(moderator, event("Entreno", Map.of("communityId", community.getId(), "capacity", 10))));

        update(moderator, id, Map.of("title", "Entreno de series", "distanceKm", 12.5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Entreno de series"))
                .andExpect(jsonPath("$.distanceKm").value(12.5));
        update(owner, id, Map.of("city", "Vigo")).andExpect(status().isOk()).andExpect(jsonPath("$.canEdit").value(true));
        update(other, id, Map.of("city", "Lugo")).andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE events SET attendee_count = 4 WHERE id = ?", id);
        update(moderator, id, Map.of("capacity", 3)).andExpect(status().isConflict());
        update(moderator, id, Map.of("capacity", 4)).andExpect(status().isOk());
        update(moderator, id, Map.of("startsAt", Instant.now().minus(1, ChronoUnit.HOURS).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_isIdempotent_blocksEditing_hidesItFromTheListing_andIsPublishedOnce() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String title = testData.uniqueName("Cancelada");
        long id = idOf(create(ana, event(title)));

        mockMvc.perform(post("/api/events/" + id + "/cancel").with(auth.bearer(bea))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events/" + id + "/cancel").with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Alerta naranja por viento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Alerta naranja por viento"))
                .andExpect(jsonPath("$.cancelledAt").exists());
        mockMvc.perform(post("/api/events/" + id + "/cancel").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Alerta naranja por viento"));

        update(ana, id, Map.of("title", "Otra vez")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede editar un evento cancelado"));
        mockMvc.perform(get("/api/events").param("size", "50").with(auth.bearer(bea)))
                .andExpect(jsonPath("$.content[*].title").value(not(hasItem(title))));
        assertThat(applicationEvents.stream(EventCancelled.class).filter(e -> e.eventId() == id)).hasSize(1);
    }

    @Test
    void listing_isSoonestFirst_andPastEventsAreFinished() throws Exception {
        UserAccount ana = testData.createUser();
        String later = testData.uniqueName("Después");
        String sooner = testData.uniqueName("Antes");
        create(ana, event(later, Map.of("startsAt", NEXT_WEEK.plus(2, ChronoUnit.DAYS).toString())));
        long soonerId = idOf(create(ana, event(sooner, Map.of("startsAt", NEXT_WEEK.minus(5, ChronoUnit.DAYS).toString()))));

        String body = mockMvc.perform(get("/api/events").param("size", "50").with(auth.bearer(ana)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body.indexOf(sooner)).isGreaterThan(0).isLessThan(body.indexOf(later));

        jdbcTemplate.update("UPDATE events SET starts_at = now() - interval '2 days' WHERE id = ?", soonerId);
        mockMvc.perform(get("/api/events/" + soonerId).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void concurrentEdits_theStaleOneFails() throws Exception {
        UserAccount ana = testData.createUser();
        long id = idOf(create(ana, event("Dos a la vez")));
        Event stale = eventRepository.findById(id).orElseThrow();

        // Someone else saved meanwhile
        jdbcTemplate.update("UPDATE events SET title = 'Cambiado', version = version + 1 WHERE id = ?", id);
        stale.setTitle("Pisando el cambio");

        assertThatThrownBy(() -> eventRepository.save(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void timezone_acceptsOnlyRegionalIanaIds_andIsRequired() throws Exception {
        UserAccount ana = testData.createUser();

        for (String ok : new String[] {"Europe/Madrid", "America/Argentina/Buenos_Aires", "Asia/Kolkata", "Pacific/Auckland", "UTC"}) {
            create(ana, event("Zona " + ok, Map.of("timezone", ok)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.timezone").value(ok));
        }
        // Fixed offsets, ambiguous abbreviations, unofficial zones, blanks
        for (String bad : new String[] {"+02:00", "UTC+2", "GMT+2", "Etc/GMT-2", "CET", "EST", "GMT", "Z", "Marte/Olimpo", " ", ""}) {
            create(ana, event("Zona mala", Map.of("timezone", bad))).andExpect(status().isBadRequest());
        }
        Map<String, Object> noZone = event("Sin zona de verdad");
        noZone.remove("timezone");
        create(ana, noZone).andExpect(status().isBadRequest());
        create(ana, event("Zona mala", Map.of("timezone", "CET")))
                .andExpect(jsonPath("$.detail").value("Zona horaria no válida: usa un identificador como Europe/Madrid"));

        long id = idOf(create(ana, event("Editar zona")));
        update(ana, id, Map.of("timezone", "Asia/Tokyo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));
        update(ana, id, Map.of("timezone", "+02:00")).andExpect(status().isBadRequest());
        update(ana, id, Map.of("timezone", "EST")).andExpect(status().isBadRequest());
        update(ana, id, nullField("timezone")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));
    }

    @Test
    void patch_nullClearsEachOptionalField_andAbsentLeavesThemAlone() throws Exception {
        UserAccount ana = testData.createUser();
        Map<String, Object> full = new LinkedHashMap<>(event("Completo"));
        full.putAll(Map.of("description", "Larga", "endsAt", NEXT_WEEK.plus(3, ChronoUnit.HOURS).toString(),
                "latitude", 42.2, "longitude", -8.7, "city", "Vigo", "distanceKm", 12.5, "elevationGainM", 800,
                "level", "INTERMEDIATE", "capacity", 10));
        long id = idOf(create(ana, full).andExpect(status().isCreated()));

        // Absent = no change (also an empty body)
        update(ana, id, Map.of("title", "Completo 2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Larga"))
                .andExpect(jsonPath("$.endsAt").exists())
                .andExpect(jsonPath("$.latitude").value(42.2))
                .andExpect(jsonPath("$.longitude").value(-8.7))
                .andExpect(jsonPath("$.meetingPointName").value("Parking del Galiñeiro"))
                .andExpect(jsonPath("$.city").value("Vigo"))
                .andExpect(jsonPath("$.distanceKm").value(12.5))
                .andExpect(jsonPath("$.elevationGainM").value(800))
                .andExpect(jsonPath("$.level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.capacity").value(10));
        update(ana, id, Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Completo 2"));

        for (String field : new String[] {"description", "endsAt", "meetingPointName", "city", "distanceKm",
                "elevationGainM", "level", "capacity"}) {
            update(ana, id, nullField(field)).andExpect(status().isOk())
                    .andExpect(jsonPath("$." + field).doesNotExist())
                    .andExpect(jsonPath("$.title").value("Completo 2"));
        }
        // Coordinates go together
        update(ana, id, nullField("latitude")).andExpect(status().isBadRequest());
        Map<String, Object> both = nullField("latitude");
        both.put("longitude", null);
        update(ana, id, both).andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist());
        assertThat(eventRepository.findById(id).orElseThrow().getCapacity()).isNull();
    }

    @Test
    void patch_nullInARequiredFieldIsRejected_andNothingChanges() throws Exception {
        UserAccount ana = testData.createUser();
        long id = idOf(create(ana, event("Obligatorios")));

        for (String field : new String[] {"title", "type", "startsAt", "timezone"}) {
            Map<String, Object> body = nullField(field);
            body.put("city", "Cambiada");
            update(ana, id, body).andExpect(status().isBadRequest());
        }
        update(ana, id, Map.of("type", "NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/events/" + id).with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.title").value("Obligatorios"))
                .andExpect(jsonPath("$.city").doesNotExist());
    }

    @Test
    void patch_clearingTheCapacityMovesTheWaitlistUp_andKeepsOptimisticLocking() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount carla = testData.createUser();
        long id = idOf(create(ana, event("Limitado", Map.of("capacity", 2))));
        mockMvc.perform(post("/api/events/" + id + "/join").with(auth.bearer(bea))).andExpect(status().isOk());
        mockMvc.perform(post("/api/events/" + id + "/join").with(auth.bearer(carla)))
                .andExpect(jsonPath("$.myStatus").value("WAITLIST"));

        update(ana, id, nullField("capacity")).andExpect(status().isOk()).andExpect(jsonPath("$.attendeeCount").value(3));
        mockMvc.perform(get("/api/events/" + id).with(auth.bearer(carla)))
                .andExpect(jsonPath("$.myStatus").value("GOING"));

        // A stale writer still loses after a PATCH (version bumped)
        Event stale = eventRepository.findById(id).orElseThrow();
        update(ana, id, Map.of("city", "Lugo")).andExpect(status().isOk());
        stale.setTitle("Pisando");
        assertThatThrownBy(() -> eventRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private static Map<String, Object> nullField(String field) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, null);
        return body;
    }

    private Map<String, Object> event(String title) {
        return event(title, Map.of());
    }

    private Map<String, Object> event(String title, Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", NEXT_WEEK.toString());
        body.put("meetingPointName", "Parking del Galiñeiro");
        body.putAll(overrides);
        return body;
    }

    private ResultActions create(UserAccount user, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/events").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions update(UserAccount user, long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(patch("/api/events/" + id).with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
