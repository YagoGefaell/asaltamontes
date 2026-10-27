package io.github.yagogefaell.asaltamontes.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationTargetType;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService.NewNotification;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T18a: what creates notifications, who receives them and the API to read them. */
class NotificationsIT extends AbstractIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private NotificationService notificationService;
    @Autowired private JdbcTemplate jdbc;

    // ---- creation, one case per type ----

    @Test
    void follow_notifiesTheFollowedOne_andNobodyElse() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        follow(ana, bea);

        JsonNode item = awaitItems(bea, 1).get(0);
        assertThat(item.get("type").asText()).isEqualTo("NEW_FOLLOWER");
        assertThat(item.get("actor").get("username").asText()).isEqualTo(ana.getUsername());
        assertThat(item.get("target").get("type").asText()).isEqualTo("USER");
        assertThat(item.get("target").get("ref").asText()).isEqualTo(ana.getUsername());
        assertThat(item.get("readAt").isNull()).isTrue();
        assertThat(item.has("recipientId")).isFalse();
        assertThat(items(ana)).isEmpty();
    }

    @Test
    void followUnfollowFollow_doesNotPileUpUnreadNotifications_norPingAgainAfterReadingIt() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        follow(ana, bea);
        awaitItems(bea, 1);

        unfollow(ana, bea);
        follow(ana, bea);
        // A different follower in between proves the async queue has drained past the repeated follow
        UserAccount cris = testData.createUser();
        follow(cris, bea);
        assertThat(awaitItems(bea, 2)).hasSize(2);

        mockMvc.perform(post("/api/notifications/read").with(auth.bearer(bea))
                .contentType(MediaType.APPLICATION_JSON).content("{\"all\":true}")).andExpect(status().isNoContent());
        unfollow(ana, bea);
        follow(ana, bea);
        // Within the cooldown it does not ping her again; a different follower still does
        UserAccount dora = testData.createUser();
        follow(dora, bea);
        assertThat(awaitItems(bea, 3)).hasSize(3);
    }

    @Test
    void joiningAnEvent_notifiesTheOrganizer_withTheEventZone_andWaitlistFlag() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID event = newEvent(organizer, 2);

        join(bea, event);
        join(cris, event);

        List<JsonNode> items = awaitItems(organizer, 2);
        JsonNode waitlisted = items.stream().filter(i -> i.get("actor").get("username").asText().equals(cris.getUsername())).findFirst().orElseThrow();
        JsonNode going = items.stream().filter(i -> i.get("actor").get("username").asText().equals(bea.getUsername())).findFirst().orElseThrow();
        assertThat(going.get("type").asText()).isEqualTo("EVENT_JOINED");
        assertThat(going.get("target").get("type").asText()).isEqualTo("EVENT");
        assertThat(going.get("target").get("ref").asText()).isEqualTo(event.toString());
        assertThat(going.get("payload").get("timeZone").asText()).isEqualTo("Asia/Tokyo");
        assertThat(Instant.parse(going.get("payload").get("startsAt").asText())).isAfter(Instant.now());
        assertThat(going.get("payload").get("title").asText()).startsWith("Quedada");
        assertThat(going.get("payload").get("waitlisted").asBoolean()).isFalse();
        assertThat(waitlisted.get("payload").get("waitlisted").asBoolean()).isTrue();
        assertThat(items(bea)).isEmpty();
        assertThat(items(cris)).isEmpty();
    }

    @Test
    void editingWhenOrWhere_notifiesGoingAndWaitlisted_butNotTheEditor_norOtherEdits() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID event = newEvent(organizer, 2);
        join(bea, event);
        join(cris, event);
        awaitItems(organizer, 2);

        patchEvent(organizer, event, Map.of("description", "Solo cambia la descripción"));
        Instant newStart = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        patchEvent(organizer, event, Map.of("startsAt", newStart.toString(), "meetingPointName", "Otro sitio"));

        for (UserAccount attendee : List.of(bea, cris)) {
            JsonNode item = awaitItems(attendee, 1).get(0);
            assertThat(item.get("type").asText()).isEqualTo("EVENT_UPDATED");
            assertThat(item.get("payload").get("changed")).extracting(JsonNode::asText).containsExactlyInAnyOrder("startsAt", "meetingPoint");
            assertThat(item.get("payload").get("timeZone").asText()).isEqualTo("Asia/Tokyo");
            assertThat(Instant.parse(item.get("payload").get("startsAt").asText())).isEqualTo(newStart);
        }
        // The organizer only has the two EVENT_JOINED
        assertThat(items(organizer)).extracting(i -> i.get("type").asText()).containsOnly("EVENT_JOINED");
    }

    @Test
    void cancelling_notifiesEveryActiveAttendeeInOneBatch_notTheOneWhoCancelled_norThoseWhoLeft() throws Exception {
        UserAccount organizer = testData.createUser();
        UUID event = newEvent(organizer, 20);
        List<UserAccount> going = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UserAccount runner = testData.createUser();
            going.add(runner);
            join(runner, event);
        }
        UserAccount left = going.remove(0);
        mockMvc.perform(post("/api/events/" + event + "/leave").with(auth.bearer(left))).andExpect(status().isOk());
        awaitCount(organizer, 20);

        mockMvc.perform(post("/api/events/" + event + "/cancel").with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Viento\"}")).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE type = 'EVENT_CANCELLED' AND target_ref = ?", Integer.class, event.toString()))
                .isEqualTo(19));
        JsonNode item = awaitItems(going.get(0), 1).get(0);
        assertThat(item.get("type").asText()).isEqualTo("EVENT_CANCELLED");
        assertThat(item.get("payload").get("reason").asText()).isEqualTo("Viento");
        assertThat(item.get("actor").get("username").asText()).isEqualTo(organizer.getUsername());
        assertThat(items(left)).isEmpty();
        assertThat(items(organizer)).extracting(i -> i.get("type").asText()).doesNotContain("EVENT_CANCELLED");
    }

    @Test
    void waitlistPromotion_notifiesThePromotedOne_withoutActor() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID event = newEvent(organizer, 2);
        join(bea, event);
        join(cris, event);

        mockMvc.perform(post("/api/events/" + event + "/leave").with(auth.bearer(bea))).andExpect(status().isOk());

        JsonNode item = awaitItems(cris, 1).get(0);
        assertThat(item.get("type").asText()).isEqualTo("WAITLIST_PROMOTED");
        assertThat(item.get("actor").isNull()).isTrue();
        assertThat(item.get("target").get("ref").asText()).isEqualTo(event.toString());
        assertThat(items(bea)).isEmpty();
    }

    @Test
    void groupCreationAndAddingMembers_notifyTheNewMembersOnly() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UserAccount dora = testData.createUser();

        UUID group = testData.createGroup(ana, bea, cris);
        mockMvc.perform(post("/api/conversations/" + group + "/participants").with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("usernames", List.of(dora.getUsername())))))
                .andExpect(status().isOk());

        for (UserAccount member : List.of(bea, cris, dora)) {
            JsonNode item = awaitItems(member, 1).get(0);
            assertThat(item.get("type").asText()).isEqualTo("ADDED_TO_GROUP");
            assertThat(item.get("actor").get("username").asText()).isEqualTo(ana.getUsername());
            assertThat(item.get("target").get("type").asText()).isEqualTo("CONVERSATION");
            assertThat(item.get("target").get("ref").asText()).isEqualTo(group.toString());
            assertThat(item.get("payload").get("groupName").asText()).startsWith("group_");
        }
        assertThat(items(ana)).isEmpty();
    }

    @Test
    void editingAgainWhileUnread_replacesThePreviousNotification() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID event = newEvent(organizer, 5);
        join(bea, event);
        patchEvent(organizer, event, Map.of("startsAt", Instant.now().plus(5, ChronoUnit.DAYS).toString()));
        awaitItems(bea, 1);
        Instant latest = Instant.now().plus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        patchEvent(organizer, event, Map.of("startsAt", latest.toString()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<JsonNode> items = items(bea);
            assertThat(items).hasSize(1);
            assertThat(Instant.parse(items.get(0).get("payload").get("startsAt").asText())).isEqualTo(latest);
        });
    }

    // ---- rules when creating ----

    @Test
    void anActionOnOneself_isNotNotified() {
        UserAccount ana = testData.createUser();

        var created = notificationService.create(new NewNotification(NotificationType.NEW_FOLLOWER, ana.getId(),
                NotificationTargetType.USER, ana.getUsername(), Map.of()), List.of(ana.getId()));

        assertThat(created).isEmpty();
    }

    @Test
    void aDisabledType_isNeitherCreatedNorSent_andOthersStillAre() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        patchPreferences(bea, "{\"types\":{\"NEW_FOLLOWER\":false}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.types.NEW_FOLLOWER").value(false))
                .andExpect(jsonPath("$.types.EVENT_JOINED").value(true));

        follow(ana, bea);
        follow(ana, cris);

        awaitItems(cris, 1);
        assertThat(items(bea)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class, bea.getId())).isZero();
    }

    @Test
    void aBlockEitherWay_stopsTheNotification() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID event = newEvent(organizer, 5);
        join(bea, event);
        join(cris, event);
        awaitItems(organizer, 2);

        // The organizer blocks bea: bea joined before, but the edit does not reach her; cris is still told
        mockMvc.perform(post("/api/users/{u}/block", bea.getUsername()).with(auth.bearer(organizer))).andExpect(status().isNoContent());
        patchEvent(organizer, event, Map.of("startsAt", Instant.now().plus(6, ChronoUnit.DAYS).toString()));

        awaitItems(cris, 1);
        assertThat(items(bea)).isEmpty();
    }

    @Test
    void whenTheRecipientBlocksTheActor_herNotificationsAreHiddenOnRead_butNotDeleted_andComeBackOnUnblock() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        for (int i = 0; i < 3; i++) {
            // One unread per actor (unique index); the rest read
            Instant readAt = i == 0 ? null : Instant.now();
            insert(me, ana, "{\"n\":" + i + "}", readAt);
            insert(me, bea, "{\"n\":" + i + "}", readAt);
        }
        insert(me, null, "{}", null); // no actor: never hidden
        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(me))).andExpect(jsonPath("$.count").value(3));

        mockMvc.perform(post("/api/users/{u}/block", ana.getUsername()).with(auth.bearer(me))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(me))).andExpect(jsonPath("$.count").value(2));
        assertThat(items(me)).hasSize(4).allSatisfy(n -> assertThat(n.path("actor").path("username").asText())
                .isNotEqualTo(ana.getUsername()));
        // The pages skip them too, with a cursor
        JsonNode first = getJson(me, "/api/notifications?limit=2");
        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        JsonNode second = getJson(me, "/api/notifications?limit=2&before=" + first.get("nextBefore").asText());
        assertThat(second.get("items")).hasSize(2);
        assertThat(second.get("hasMore").asBoolean()).isFalse();
        // Nothing was deleted
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class, me.getId())).isEqualTo(7);
        // It is one way: the blocked one still sees her own
        insert(ana, me, "{}", null);
        assertThat(items(ana)).hasSize(1);

        mockMvc.perform(delete("/api/users/{u}/block", ana.getUsername()).with(auth.bearer(me))).andExpect(status().isNoContent());
        assertThat(items(me)).hasSize(7);
        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(me))).andExpect(jsonPath("$.count").value(3));
    }

    // ---- the API ----

    @Test
    void list_pagesNewestFirst_withOpaqueCursor_andCapsTheLimitAt50() throws Exception {
        UserAccount bea = testData.createUser();
        for (int i = 0; i < 55; i++) {
            insert(bea, null, "{\"n\":" + i + "}", null);
        }

        JsonNode first = getJson(bea, "/api/notifications?limit=500");
        assertThat(first.get("items")).hasSize(50);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        assertThat(first.get("items").get(0).get("payload").get("n").asInt()).isEqualTo(54);
        String cursor = first.get("nextBefore").asText();
        assertThat(cursor).doesNotMatch("\\d+").doesNotContain(first.get("items").get(49).get("id").asText());

        JsonNode second = getJson(bea, "/api/notifications?limit=50&before=" + cursor);
        assertThat(second.get("items")).hasSize(5);
        assertThat(second.get("hasMore").asBoolean()).isFalse();
        assertThat(second.get("nextBefore").isNull()).isTrue();
        assertThat(second.get("items").get(0).get("payload").get("n").asInt()).isEqualTo(4);

        JsonNode defaults = getJson(bea, "/api/notifications");
        assertThat(defaults.get("items")).hasSize(20);
    }

    @Test
    void list_aCursorOfSomeoneElseOrGarbage_is400() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        insert(ana, null, "{}", null);
        insert(bea, null, "{}", null);
        String anasCursor = getJson(ana, "/api/notifications?limit=1").get("items").get(0).get("id").asText();
        // A well formed cursor naming her notification, used by bea
        String token = io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor.encode("n",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), UUID.fromString(anasCursor));

        mockMvc.perform(get("/api/notifications").param("before", token).with(auth.bearer(bea)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Cursor no válido"));
        mockMvc.perform(get("/api/notifications").param("before", "garbage").with(auth.bearer(bea)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications").param("before", anasCursor).with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCount_andMarkingAsRead() throws Exception {
        UserAccount bea = testData.createUser();
        for (int i = 0; i < 3; i++) {
            insert(bea, null, "{}", null);
        }
        List<JsonNode> items = items(bea);
        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(bea)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));

        read(bea, "{\"ids\":[\"" + items.get(0).get("id").asText() + "\"]}").andExpect(status().isNoContent());
        read(bea, "{\"ids\":[\"" + items.get(0).get("id").asText() + "\"]}").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(bea))).andExpect(jsonPath("$.count").value(2));
        assertThat(items(bea).get(0).get("readAt").isNull()).isFalse();

        read(bea, "{\"all\":true}").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").with(auth.bearer(bea))).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markingSomeoneElsesOrMissingNotifications_is404_identical_andMarksNothing() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        insert(ana, null, "{}", null);
        insert(bea, null, "{}", null);
        String mine = items(bea).get(0).get("id").asText();
        String anas = items(ana).get(0).get("id").asText();

        String foreign = read(bea, "{\"ids\":[\"" + mine + "\",\"" + anas + "\"]}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notificación no encontrada")).andReturn().getResponse().getContentAsString();
        String missing = read(bea, "{\"ids\":[\"" + mine + "\",\"" + UUID.randomUUID() + "\"]}").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(foreign).get("detail")).isEqualTo(objectMapper.readTree(missing).get("detail"));
        assertThat(items(bea).get(0).get("readAt").isNull()).isTrue();
        assertThat(items(ana).get(0).get("readAt").isNull()).isTrue();
    }

    @Test
    void markRead_validatesTheBody() throws Exception {
        UserAccount bea = testData.createUser();
        read(bea, "{}").andExpect(status().isBadRequest());
        read(bea, "{\"ids\":[]}").andExpect(status().isBadRequest());
        read(bea, "{\"ids\":[\"" + UUID.randomUUID() + "\"],\"all\":true}").andExpect(status().isBadRequest());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add("\"" + UUID.randomUUID() + "\"");
        }
        read(bea, "{\"ids\":[" + String.join(",", many) + "]}").andExpect(status().isBadRequest());
    }

    @Test
    void preferences_listEveryType_patchIsPartial_andNullOrUnknownTypesAre400() throws Exception {
        UserAccount bea = testData.createUser();
        mockMvc.perform(get("/api/notifications/preferences").with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$.types.NEW_FOLLOWER").value(true));

        patchPreferences(bea, "{\"types\":{\"NEW_FOLLOWER\":false}}").andExpect(status().isOk());
        patchPreferences(bea, "{\"types\":{\"EVENT_JOINED\":false}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.types.NEW_FOLLOWER").value(false))
                .andExpect(jsonPath("$.types.EVENT_JOINED").value(false))
                .andExpect(jsonPath("$.types.ADDED_TO_GROUP").value(true));
        patchPreferences(bea, "{\"types\":{\"NEW_FOLLOWER\":true}}").andExpect(jsonPath("$.types.NEW_FOLLOWER").value(true));

        patchPreferences(bea, "{\"types\":{\"NEW_FOLLOWER\":null}}").andExpect(status().isBadRequest());
        patchPreferences(bea, "{\"types\":{\"NOPE\":false}}").andExpect(status().isBadRequest());
        patchPreferences(bea, "{}").andExpect(status().isBadRequest());
        // A rejected body changes nothing, not even the valid part
        patchPreferences(bea, "{\"types\":{\"ADDED_TO_GROUP\":false,\"NOPE\":false}}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications/preferences").with(auth.bearer(bea)))
                .andExpect(jsonPath("$.types.ADDED_TO_GROUP").value(true));
    }

    @Test
    void aFailingNotification_neverBreaksTheAction() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        // Make inserting fail for this recipient: the follow must still succeed
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_notification() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'boom'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER trg_fail_notification BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION fail_notification()");
        try {
            follow(ana, bea);
            Thread.sleep(500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_follow WHERE following_id = ?", Integer.class, bea.getId())).isOne();
        } finally {
            jdbc.execute("DROP TRIGGER trg_fail_notification ON notifications");
        }
        assertThat(items(bea)).isEmpty();
    }

    // ---- helpers ----

    private void follow(UserAccount who, UserAccount target) throws Exception {
        mockMvc.perform(post("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who))).andExpect(status().isOk());
    }

    private void unfollow(UserAccount who, UserAccount target) throws Exception {
        mockMvc.perform(delete("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who))).andExpect(status().isNoContent());
    }

    private UUID newEvent(UserAccount organizer, Integer capacity) {
        return eventService.create(new CreateEventDto(testData.uniqueName("Quedada"), null, EventType.MEETUP,
                Instant.now().plus(3, ChronoUnit.DAYS), null, "Asia/Tokyo", "Parking", null, null, "Tokio", null, null, null,
                capacity, EventVisibility.PUBLIC, null), organizer).id();
    }

    private void join(UserAccount user, UUID event) throws Exception {
        mockMvc.perform(post("/api/events/" + event + "/join").with(auth.bearer(user))).andExpect(status().isOk());
    }

    private void patchEvent(UserAccount user, UUID event, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch("/api/events/" + event).with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private ResultActions read(UserAccount user, String body) throws Exception {
        return mockMvc.perform(post("/api/notifications/read").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchPreferences(UserAccount user, String body) throws Exception {
        return mockMvc.perform(patch("/api/notifications/preferences").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode getJson(UserAccount user, String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).with(auth.bearer(user))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<JsonNode> items(UserAccount user) throws Exception {
        List<JsonNode> items = new ArrayList<>();
        getJson(user, "/api/notifications?limit=50").get("items").forEach(items::add);
        return items;
    }

    /** Notifications are made asynchronously after the commit: wait until the list has the expected size. */
    private List<JsonNode> awaitItems(UserAccount user, int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(items(user)).hasSize(expected));
        try {
            return items(user);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void awaitCount(UserAccount user, int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class, user.getId())).isEqualTo(expected));
    }

    /** Direct insert, for tests of the API that need many notifications without going through the listener. */
    private void insert(UserAccount recipient, UserAccount actor, String payload, Instant readAt) {
        jdbc.update("INSERT INTO notifications (public_id, recipient_id, actor_id, type, payload, read_at) VALUES (?, ?, ?, 'NEW_FOLLOWER', ?::jsonb, ?)",
                io.github.yagogefaell.asaltamontes.common.identifier.UuidV7.next(), recipient.getId(),
                actor == null ? null : actor.getId(), payload, readAt == null ? null : java.sql.Timestamp.from(readAt));
    }
}
