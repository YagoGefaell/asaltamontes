package io.github.yagogefaell.asaltamontes.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.persistence.EntityManagerFactory;

/** T31: {@code attendeePreview} of the event listing. The organizer is the first attendee of her event. */
class EventAttendeePreviewIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final Instant BASE = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Test
    void preview_hasTheThreeOldestGoing_withoutBlockedOrWaitlisted_andAvatarThumb() throws Exception {
        UserAccount organizer = testData.createUser();
        String city = testData.uniqueName("Preview");
        UUID event = create(organizer, city, "unica", 0, 7);
        UserAccount first = testData.createUser();
        UserAccount blockedByMe = testData.createUser();
        UserAccount blocksMe = testData.createUser();
        UserAccount second = testData.createUser();
        UserAccount third = testData.createUser();
        UserAccount me = testData.createUser();
        UserAccount waitlisted = testData.createUser();
        jdbcTemplate.update("UPDATE user_profiles SET profile_picture_url = ? WHERE user_id = ?",
                "https://example.com/pic.jpg", organizer.getId());
        for (UserAccount u : List.of(first, blockedByMe, blocksMe, second, third, me, waitlisted)) {
            join(u, event);
        }
        mockMvc.perform(post("/api/users/{u}/block", blockedByMe.getUsername()).with(auth.bearer(me))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/users/{u}/block", me.getUsername()).with(auth.bearer(blocksMe))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events").with(auth.bearer(me)).param("city", city))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].attendeePreview[*].username")
                        .value(contains(organizer.getUsername(), first.getUsername(), second.getUsername())))
                .andExpect(jsonPath("$.content[0].attendeePreview[0].avatar.thumb").value("https://example.com/pic.jpg"))
                .andExpect(jsonPath("$.content[0].attendeePreview[1].avatar").doesNotExist())
                .andExpect(jsonPath("$.content[0].attendeePreview[0].id").doesNotExist());
        // Someone with no blocks sees the three oldest
        mockMvc.perform(get("/api/events").with(auth.bearer(organizer)).param("city", city))
                .andExpect(jsonPath("$.content[0].attendeePreview[*].username")
                        .value(contains(organizer.getUsername(), first.getUsername(), blockedByMe.getUsername())));
    }

    @Test
    void preview_costsTheSameNumberOfStatements_withOneEventAndWithTwenty() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount viewer = testData.createUser();
        List<UserAccount> people = List.of(testData.createUser(), testData.createUser(), testData.createUser(), testData.createUser());
        String one = testData.uniqueName("Uno");
        join(people.get(0), create(organizer, one, "solo", 0, null));
        String many = testData.uniqueName("Veinte");
        for (int i = 0; i < 20; i++) {
            UUID event = create(organizer, many, "e" + i, i, null);
            for (UserAccount p : people) {
                join(p, event);
            }
        }

        long forOne = statements(viewer, one);
        long forTwenty = statements(viewer, many);
        assertThat(forTwenty).isEqualTo(forOne);
        mockMvc.perform(get("/api/events").with(auth.bearer(viewer)).param("city", many).param("size", "50"))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[19].attendeePreview.length()").value(3));
    }

    private long statements(UserAccount user, String city) throws Exception {
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        // warm-up: the first request of a user also loads her account
        mockMvc.perform(get("/api/events").with(auth.bearer(user)).param("city", city).param("size", "50"));
        stats.clear();
        // size 50 on a page that is not full: the count query is skipped in both cases
        mockMvc.perform(get("/api/events").with(auth.bearer(user)).param("city", city).param("size", "50"))
                .andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }

    private UUID create(UserAccount user, String city, String name, int dayOffset, Integer capacity) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("title", name + " " + city, "type", "MEETUP",
                "timezone", "Europe/Madrid", "startsAt", BASE.plus(dayOffset, ChronoUnit.DAYS).toString(),
                "meetingPointName", "Punto", "city", city));
        if (capacity != null) {
            body.put("capacity", capacity);
        }
        ResultActions r = mockMvc.perform(post("/api/events").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isCreated());
        return UUID.fromString(objectMapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private void join(UserAccount user, UUID event) throws Exception {
        mockMvc.perform(post("/api/events/" + event + "/join").with(auth.bearer(user))).andExpect(status().isOk());
        Thread.sleep(5); // distinct joined_at, so "oldest first" is deterministic
    }
}
