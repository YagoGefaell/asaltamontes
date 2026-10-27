package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.maintenance.EventReminderJob;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T21: the chat of each event follows its GOING attendees; reminders; calendar file. */
class EventChatIT extends AbstractIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private MessageService messageService;
    @Autowired private EventReminderJob reminderJob;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void theChatExistsFromTheStart_andOnlyThoseGoingGetIt() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount waiting = testData.createUser();
        UserAccount outsider = testData.createUser();
        UUID event = newEvent(organizer, 1);
        join(waiting, event).andExpect(status().isOk()).andExpect(jsonPath("$.myStatus").value("WAITLIST"));

        UUID chat = chatOf(organizer, event);

        mockMvc.perform(get("/api/conversations").with(auth.bearer(organizer)))
                .andExpect(jsonPath("$.items[0].conversationId").value(chat.toString()));
        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(organizer)))
                .andExpect(jsonPath("$.managed").value(true))
                .andExpect(jsonPath("$.participantCount").value(1));
        // The waitlist and strangers get the same 404 as an event that does not exist
        chatRequest(waiting, event).andExpect(status().isNotFound());
        chatRequest(outsider, event).andExpect(status().isNotFound());
        chatRequest(outsider, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(waiting))).andExpect(status().isNotFound());
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, outsider.getId(), "hola"))
                .isInstanceOf(io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException.class);
    }

    @Test
    void joiningLeavingAndBeingPromoted_keepTheChatInSync() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID event = newEvent(organizer, 2);
        UUID chat = chatOf(organizer, event);

        join(ana, event).andExpect(status().isOk());
        join(bea, event).andExpect(jsonPath("$.myStatus").value("WAITLIST"));
        assertThat(participants(chat)).isEqualTo(2);
        chatRequest(bea, event).andExpect(status().isNotFound());

        // Ana leaves: Bea is promoted and gets the chat
        leave(ana, event).andExpect(status().isOk());
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(ana))).andExpect(status().isNotFound());
        assertThat(chatOf(bea, event)).isEqualTo(chat);
        assertThat(participants(chat)).isEqualTo(2);

        // She can come back
        leave(bea, event);
        join(bea, event);
        assertThat(participants(chat)).isEqualTo(2);
    }

    @Test
    void aNewcomer_seesTheWholeHistory() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount late = testData.createUser();
        UUID event = newEvent(organizer, null);
        UUID chat = chatOf(organizer, event);
        messageService.processIncomingMessage(chat, organizer.getId(), "primero");
        messageService.processIncomingMessage(chat, organizer.getId(), "segundo");

        join(late, event).andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(late)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].content").value("primero"))
                .andExpect(jsonPath("$.items[1].content").value("segundo"));
    }

    @Test
    void nobodyManagesTheMembersByHand_andLeavingIsLeavingTheEvent() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount other = testData.createUser();
        UUID event = newEvent(organizer, null);
        UUID chat = chatOf(organizer, event);
        join(ana, event);

        mockMvc.perform(post("/api/conversations/" + chat + "/participants").with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + other.getUsername() + "\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/conversations/" + chat + "/participants/" + ana.getUsername()).with(auth.bearer(organizer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/conversations/" + chat).with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupName\":\"otro\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations/" + chat + "/leave").with(auth.bearer(ana))).andExpect(status().isConflict());
        assertThat(participants(chat)).isEqualTo(2);
    }

    @Test
    void renamingTheEvent_renamesTheChat() throws Exception {
        UserAccount organizer = testData.createUser();
        UUID event = newEvent(organizer, null);
        UUID chat = chatOf(organizer, event);

        patchEvent(organizer, event, "{\"title\":\"Nuevo título\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(organizer)))
                .andExpect(jsonPath("$.name").value("Nuevo título"));
    }

    @Test
    void movingTheEvent_leavesASystemMessage_andResetsTheReminders() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UUID event = newEvent(organizer, null);
        join(ana, event);
        UUID chat = chatOf(ana, event);
        jdbc.update("UPDATE events SET reminder_24h_sent_at = now(), reminder_2h_sent_at = now() WHERE public_id = ?", event);

        // Title only: nothing to tell
        patchEvent(organizer, event, "{\"description\":\"con barro\"}").andExpect(status().isOk());
        assertThat(messages(ana, chat)).isEmpty();

        String newStart = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        patchEvent(organizer, event, "{\"startsAt\":\"" + newStart + "\"}").andExpect(status().isOk());

        JsonNode message = messages(ana, chat).get(0);
        assertThat(message.get("type").asText()).isEqualTo("SYSTEM");
        assertThat(message.get("senderUsername").isNull()).isTrue();
        assertThat(message.get("systemPayload").get("action").asText()).isEqualTo("EVENT_UPDATED");
        assertThat(message.get("systemPayload").get("changed").get(0).asText()).isEqualTo("startsAt");
        assertThat(message.get("systemPayload").get("startsAt").asText()).isEqualTo(newStart);
        assertThat(message.get("systemPayload").get("timeZone").asText()).isEqualTo("Europe/Madrid");
        assertThat(jdbc.queryForObject("SELECT reminder_24h_sent_at IS NULL AND reminder_2h_sent_at IS NULL FROM events WHERE public_id = ?",
                Boolean.class, event)).isTrue();
        // Unread for the attendee, not for the organizer who did it
        assertThat(unread(ana, chat)).isEqualTo(1);
        assertThat(unread(organizer, chat)).isZero();
    }

    @Test
    void cancelling_leavesASystemMessage_andTheChatIsReadOnly() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UUID event = newEvent(organizer, null);
        join(ana, event);
        UUID chat = chatOf(ana, event);
        messageService.processIncomingMessage(chat, ana.getId(), "¿llevo bastones?");

        mockMvc.perform(post("/api/events/" + event + "/cancel").with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Viento\"}")).andExpect(status().isOk());

        JsonNode last = messages(ana, chat).get(1);
        assertThat(last.get("systemPayload").get("action").asText()).isEqualTo("EVENT_CANCELLED");
        assertThat(last.get("systemPayload").get("reason").asText()).isEqualTo("Viento");
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, ana.getId(), "vaya"))
                .isInstanceOf(ConflictException.class);
        JsonNode state = json(chatRequest(ana, event).andExpect(status().isOk()));
        assertThat(state.get("canWrite").asBoolean()).isFalse();
        assertThat(state.get("readOnlyReason").asText()).isEqualTo("CANCELLED");
        // The history stays readable
        assertThat(messages(ana, chat)).hasSize(2);
    }

    @Test
    void sevenDaysAfterTheEvent_theChatIsArchived_readOnlyButReadable() throws Exception {
        UserAccount organizer = testData.createUser();
        UUID event = newEvent(organizer, null);
        UUID chat = chatOf(organizer, event);
        messageService.processIncomingMessage(chat, organizer.getId(), "qué buena salida");

        // Ended 6 days ago (no end time: 12 h after it starts): still open
        jdbc.update("UPDATE events SET starts_at = now() - interval '6 days 12 hours' WHERE public_id = ?", event);
        assertThat(json(chatRequest(organizer, event).andExpect(status().isOk())).get("canWrite").asBoolean()).isTrue();
        messageService.processIncomingMessage(chat, organizer.getId(), "fotos en el grupo");

        jdbc.update("UPDATE events SET starts_at = now() - interval '8 days' WHERE public_id = ?", event);

        JsonNode state = json(chatRequest(organizer, event).andExpect(status().isOk()));
        assertThat(state.get("canWrite").asBoolean()).isFalse();
        assertThat(state.get("readOnlyReason").asText()).isEqualTo("ARCHIVED");
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, organizer.getId(), "tarde"))
                .isInstanceOf(ConflictException.class);
        assertThat(messages(organizer, chat)).hasSize(2);
    }

    @Test
    void remindersAreSentOnceTo24hAnd2hBefore_onlyToThoseGoing() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount waiting = testData.createUser();
        UserAccount left = testData.createUser();
        UUID event = newEvent(organizer, 2);
        join(ana, event);
        join(waiting, event).andExpect(jsonPath("$.myStatus").value("WAITLIST"));
        join(left, event).andExpect(jsonPath("$.myStatus").value("WAITLIST"));
        leave(left, event);
        UUID far = newEvent(organizer, null);
        UUID cancelled = newEvent(organizer, null);
        mockMvc.perform(post("/api/events/" + cancelled + "/cancel").with(auth.bearer(organizer))).andExpect(status().isOk());

        // 23.5 h from now: the 24 h reminder
        jdbc.update("UPDATE events SET starts_at = now() + interval '23 hours 30 minutes' WHERE public_id IN (?, ?)", event, cancelled);
        assertThat(reminderJob.run(Instant.now())).isEqualTo(1);
        awaitReminders(ana, 1);
        assertThat(reminderJob.run(Instant.now())).isZero();

        // The same event 90 minutes from now: the 2 h reminder, and it is not repeated
        jdbc.update("UPDATE events SET starts_at = now() + interval '90 minutes' WHERE public_id = ?", event);
        assertThat(reminderJob.run(Instant.now())).isEqualTo(1);
        assertThat(reminderJob.run(Instant.now())).isZero();
        awaitReminders(ana, 2);
        awaitReminders(organizer, 2);
        Thread.sleep(300);
        assertThat(reminders(waiting)).isZero();
        assertThat(reminders(left)).isZero();
        assertThat(reminders(ana)).isEqualTo(2);

        JsonNode payload = objectMapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM notifications WHERE recipient_id = ? AND type = 'EVENT_REMINDER' ORDER BY id DESC LIMIT 1",
                String.class, ana.getId()));
        assertThat(payload.get("hoursBefore").asInt()).isEqualTo(2);
        assertThat(payload.get("timeZone").asText()).isEqualTo("Europe/Madrid");
        assertThat(payload.has("startsAt")).isTrue();
        // The event 3 days away and the cancelled one got nothing
        assertThat(reminders(organizer)).isEqualTo(2);
        assertThat(far).isNotNull();
    }

    @Test
    void anEventCreatedSoon_doesNotGetTheTwentyFourHourReminder_andAMovedOneGetsThemAgain() throws Exception {
        UserAccount organizer = testData.createUser();
        UUID soon = newEvent(organizer, null);
        jdbc.update("UPDATE events SET starts_at = now() + interval '5 hours' WHERE public_id = ?", soon);

        assertThat(reminderJob.run(Instant.now())).isZero();

        // Moved through the API: the claims are reset and the new date has its own reminders
        UUID moved = newEvent(organizer, null);
        jdbc.update("UPDATE events SET starts_at = now() + interval '23 hours 30 minutes' WHERE public_id = ?", moved);
        assertThat(reminderJob.run(Instant.now())).isEqualTo(1);
        String newStart = Instant.now().plus(23, ChronoUnit.HOURS).plus(40, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        patchEvent(organizer, moved, "{\"startsAt\":\"" + newStart + "\"}").andExpect(status().isOk());
        assertThat(reminderJob.run(Instant.now())).isEqualTo(1);
    }

    @Test
    void calendarFile_hasTheEventInUtc_escapesTheText_andFollowsTheVisibilityOfTheEvent() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount outsider = testData.createUser();
        Instant start = Instant.parse("2027-03-14T08:30:00Z");
        UUID event = eventService.create(new CreateEventDto("Subida; al \"Pico\", con\nsalto", "Trae agua, barritas\r\nY ánimo", EventType.TRAINING,
                start, start.plus(3, ChronoUnit.HOURS), "Europe/Madrid", "Parking, norte", 42.2406, -8.7207, "Vigo", null, null, null, null,
                EventVisibility.PUBLIC, null), organizer).id();

        String ics = mockMvc.perform(get("/api/events/" + event + ".ics").with(auth.bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"quedada.ics\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:" + event + "@asaltamontes\r\n");
        assertThat(ics).contains("DTSTART:20270314T083000Z\r\n").contains("DTEND:20270314T113000Z\r\n");
        assertThat(ics).contains("SUMMARY:Subida\\; al \"Pico\"\\, con\\nsalto\r\n");
        assertThat(ics).contains("LOCATION:Parking\\, norte\\, Vigo\r\n");
        assertThat(ics).contains("GEO:42.240600;-8.720700\r\n").contains("STATUS:CONFIRMED\r\n");
        // The text cannot add lines of its own
        assertThat(ics.split("\r\n")).noneMatch(line -> line.startsWith("salto") || line.startsWith("Y "));

        mockMvc.perform(get("/api/events/" + UUID.randomUUID() + ".ics").with(auth.bearer(outsider))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/" + event + ".ics")).andExpect(status().isUnauthorized());

        // An event only for the members of a community is a 404 for anybody else, also as a file
        Community community = testData.createCommunity(organizer);
        UUID private_ = eventService.create(new CreateEventDto("Solo socias", null, EventType.MEETUP,
                Instant.now().plus(3, ChronoUnit.DAYS), null, "Europe/Madrid", null, null, null, null, null, null, null, null,
                EventVisibility.COMMUNITY, community.getPublicId()), organizer).id();
        mockMvc.perform(get("/api/events/" + private_ + ".ics").with(auth.bearer(outsider))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/" + private_ + ".ics").with(auth.bearer(organizer))).andExpect(status().isOk());
    }

    @Test
    void aCancelledEventIsMarkedCancelledInTheCalendarFile() throws Exception {
        UserAccount organizer = testData.createUser();
        UUID event = newEvent(organizer, null);
        mockMvc.perform(post("/api/events/" + event + "/cancel").with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Viento\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/events/" + event + ".ics").with(auth.bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STATUS:CANCELLED\r\n")));
    }

    // ---------------------------------------------------------------------------------------------------

    private UUID newEvent(UserAccount organizer, Integer capacity) {
        return eventService.create(new CreateEventDto(testData.uniqueName("Quedada"), null, EventType.MEETUP,
                Instant.now().plus(3, ChronoUnit.DAYS), null, "Europe/Madrid", "Parking", null, null, "Vigo", null, null, null,
                capacity, EventVisibility.PUBLIC, null), organizer).id();
    }

    private ResultActions join(UserAccount user, UUID event) throws Exception {
        return mockMvc.perform(post("/api/events/" + event + "/join").with(auth.bearer(user)));
    }

    private ResultActions leave(UserAccount user, UUID event) throws Exception {
        return mockMvc.perform(post("/api/events/" + event + "/leave").with(auth.bearer(user)));
    }

    private ResultActions patchEvent(UserAccount user, UUID event, String body) throws Exception {
        return mockMvc.perform(patch("/api/events/" + event).with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions chatRequest(UserAccount user, UUID event) throws Exception {
        return mockMvc.perform(get("/api/events/" + event + "/chat").with(auth.bearer(user)));
    }

    private UUID chatOf(UserAccount user, UUID event) throws Exception {
        return UUID.fromString(json(chatRequest(user, event).andExpect(status().isOk())).get("conversationId").asText());
    }

    private List<JsonNode> messages(UserAccount user, UUID chat) throws Exception {
        JsonNode items = json(mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(user)))
                .andExpect(status().isOk())).get("items");
        return java.util.stream.StreamSupport.stream(items.spliterator(), false).toList();
    }

    private int participants(UUID chat) {
        return jdbc.queryForObject("SELECT count(*) FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                + "WHERE c.public_id = ?", Integer.class, chat);
    }

    private int unread(UserAccount user, UUID chat) {
        return jdbc.queryForObject("SELECT p.unread_count FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                + "WHERE c.public_id = ? AND p.user_id = ?", Integer.class, chat, user.getId());
    }

    private int reminders(UserAccount user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'EVENT_REMINDER'",
                Integer.class, user.getId());
    }

    private void awaitReminders(UserAccount user, int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(reminders(user)).isEqualTo(expected));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
