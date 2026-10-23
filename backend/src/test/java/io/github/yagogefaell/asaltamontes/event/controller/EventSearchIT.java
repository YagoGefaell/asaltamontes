package io.github.yagogefaell.asaltamontes.event.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T15c: filters of GET /api/events and GET /api/communities/{slug}/events. Every test uses its own city or
 * community so the events of other tests do not get in the way.
 */
class EventSearchIT extends AbstractIntegrationTest {

    @Autowired private CommunityService communityService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final Instant BASE = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Test
    void combinedFilters_narrowTheResult_andStayPaginated() throws Exception {
        UserAccount ana = testData.createUser();
        String city = testData.uniqueName("Vigo");
        create(ana, ev("trail-corto", city, 0, Map.of("type", "TRAINING", "level", "BEGINNER", "distanceKm", 8.5)));
        create(ana, ev("trail-medio", city, 1, Map.of("type", "TRAINING", "level", "ADVANCED", "distanceKm", 15)));
        create(ana, ev("trail-largo", city, 2, Map.of("type", "RACE", "level", "ADVANCED", "distanceKm", 42)));
        create(ana, ev("sin-distancia", city, 3, Map.of("type", "MEETUP")));
        create(ana, ev("otra-ciudad", "Lugo", 0, Map.of("type", "TRAINING")));

        // city, case-insensitive, soonest first
        titles(ana, "city", city.toUpperCase()).andExpect(jsonPath("$.content[*].title")
                .value(contains(t(city, "trail-corto"), t(city, "trail-medio"), t(city, "trail-largo"), t(city, "sin-distancia"))));
        // type + level
        titles(ana, "city", city, "type", "TRAINING", "level", "ADVANCED")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "trail-medio"))));
        // distance range (events without distance never match a distance filter)
        titles(ana, "city", city, "minDistance", "10", "maxDistance", "42")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "trail-medio"), t(city, "trail-largo"))));
        titles(ana, "city", city, "maxDistance", "8.5")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "trail-corto"))));
        // from / to
        titles(ana, "city", city, "from", BASE.plus(1, ChronoUnit.DAYS).toString(), "to", BASE.plus(2, ChronoUnit.DAYS).toString())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "trail-medio"), t(city, "trail-largo"))));
        // pagination
        titles(ana, "city", city, "size", "3", "page", "1")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "sin-distancia"))))
                .andExpect(jsonPath("$.totalElements").value(4));
        // no match
        titles(ana, "city", city, "type", "TRIP").andExpect(jsonPath("$.content").value(empty()));
    }

    @Test
    void boundingBox_returnsOnlyEventsInsideIt() throws Exception {
        UserAccount ana = testData.createUser();
        String tag = testData.uniqueName("bbox");
        create(ana, ev("dentro", tag, 0, Map.of("latitude", 42.2, "longitude", -8.7)));
        create(ana, ev("fuera", tag, 1, Map.of("latitude", 40.4, "longitude", -3.7)));
        create(ana, ev("sin-coordenadas", tag, 2, Map.of()));

        mockMvc.perform(get("/api/events").with(auth.bearer(ana)).param("city", tag)
                        .param("minLat", "42").param("maxLat", "43").param("minLng", "-9").param("maxLng", "-8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "dentro"))))
                .andExpect(jsonPath("$.content[0].latitude").value(42.2))
                .andExpect(jsonPath("$.content[0].longitude").value(-8.7));
        // Events without a meeting point come back with null coordinates (BUG-062)
        mockMvc.perform(get("/api/events").with(auth.bearer(ana)).param("city", tag))
                .andExpect(jsonPath("$.content[?(@.title=='" + t(tag, "sin-coordenadas") + "')].latitude").value(contains((Object) null)));
        // The bounding box alone (no city): a box around Madrid finds the Madrid event
        mockMvc.perform(get("/api/events").with(auth.bearer(ana))
                        .param("size", "50").param("minLat", "40.3").param("maxLat", "40.5").param("minLng", "-3.8").param("maxLng", "-3.6"))
                .andExpect(jsonPath("$.content[*].title").value(org.hamcrest.Matchers.hasItem(t(tag, "fuera"))));

        // incomplete or inverted boxes are rejected
        mockMvc.perform(get("/api/events").with(auth.bearer(ana)).param("minLat", "42")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events").with(auth.bearer(ana))
                        .param("minLat", "43").param("maxLat", "42").param("minLng", "-9").param("maxLng", "-8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizerMe_andAttendingMe_areRelativeToTheCaller() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String city = testData.uniqueName("Ourense");
        create(ana, ev("de-ana", city, 0, Map.of()));
        long beas = idOf(create(bea, ev("de-bea", city, 1, Map.of())));
        long full = idOf(create(bea, ev("completo", city, 2, Map.of("capacity", 1))));
        long cancelled = idOf(create(bea, ev("cancelado", city, 3, Map.of())));
        act(bea, "/api/events/" + cancelled + "/cancel");
        act(ana, "/api/events/" + beas + "/join");
        act(ana, "/api/events/" + full + "/join"); // waitlist

        titles(ana, "city", city, "organizer", "me")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "de-ana"))));
        titles(ana, "city", city, "attending", "me") // GOING and WAITLIST, not cancelled events
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "de-ana"), t(city, "de-bea"), t(city, "completo"))));
        titles(bea, "city", city, "organizer", "me", "attending", "me")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "de-bea"), t(city, "completo"))));
        titles(ana, "city", city, "organizer", "someone-else").andExpect(status().isBadRequest());
        // she leaves: no longer attending
        act(ana, "/api/events/" + beas + "/leave");
        titles(ana, "city", city, "attending", "me")
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "de-ana"), t(city, "completo"))));
    }

    @Test
    void communityEvents_respectVisibility_inEveryListing() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        UserAccount banned = testData.createUser();
        Community community = testData.createCommunity(owner);
        Community other = testData.createCommunity(owner);
        communityService.joinCommunity(community.getId(), member);
        communityService.joinCommunity(community.getId(), banned);
        jdbcTemplate.update("UPDATE community_members SET status = 'BANNED' WHERE user_account_id = ?", banned.getId());

        String city = testData.uniqueName("Pontevedra");
        create(owner, ev("publico", city, 0, Map.of("communityId", community.getId())));
        create(owner, ev("privado", city, 1, Map.of("communityId", community.getId(), "visibility", "COMMUNITY")));
        create(owner, ev("de-la-otra", city, 2, Map.of("communityId", other.getId())));
        create(owner, ev("sin-comunidad", city, 3, Map.of()));

        String url = "/api/communities/" + community.getSlug() + "/events";
        mockMvc.perform(get(url).with(auth.bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"), t(city, "privado"))));
        mockMvc.perform(get(url).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"), t(city, "privado"))));
        mockMvc.perform(get(url).with(auth.bearer(outsider)))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"))));
        mockMvc.perform(get(url).with(auth.bearer(banned)))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"))));
        mockMvc.perform(get(url).param("type", "TRIP").with(auth.bearer(member)))
                .andExpect(jsonPath("$.content").value(empty()));
        mockMvc.perform(get("/api/communities/no-such-community/events").with(auth.bearer(member)))
                .andExpect(status().isNotFound());

        // The COMMUNITY event never leaks through any filter of the general listing either
        titles(outsider, "city", city).andExpect(jsonPath("$.content[*].title")
                .value(containsInAnyOrder(t(city, "publico"), t(city, "de-la-otra"), t(city, "sin-comunidad"))));
        titles(outsider, "city", city, "communityId", String.valueOf(community.getId()))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"))));
        titles(member, "city", city, "communityId", String.valueOf(community.getId()))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(city, "publico"), t(city, "privado"))));
    }

    @Test
    void invalidFilters_areRejected() throws Exception {
        UserAccount ana = testData.createUser();
        titles(ana, "type", "PICNIC").andExpect(status().isBadRequest());
        titles(ana, "level", "GOD").andExpect(status().isBadRequest());
        titles(ana, "from", "yesterday").andExpect(status().isBadRequest());
        titles(ana, "from", BASE.toString(), "to", BASE.minus(1, ChronoUnit.DAYS).toString()).andExpect(status().isBadRequest());
        titles(ana, "minDistance", "20", "maxDistance", "10").andExpect(status().isBadRequest());
    }

    // Title unique per tag, so tests do not see each other's events
    private static String t(String tag, String name) {
        return name + " " + tag;
    }

    private Map<String, Object> ev(String name, String city, int dayOffset, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", t(city, name));
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", BASE.plus(dayOffset, ChronoUnit.DAYS).toString());
        body.put("meetingPointName", "Punto de encuentro");
        body.put("city", city);
        body.putAll(extra);
        return body;
    }

    private ResultActions create(UserAccount user, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/events").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
    }

    private void act(UserAccount user, String url) throws Exception {
        mockMvc.perform(post(url).with(auth.bearer(user))).andExpect(status().isOk());
    }

    private ResultActions titles(UserAccount user, String... params) throws Exception {
        var request = get("/api/events").with(auth.bearer(user));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
