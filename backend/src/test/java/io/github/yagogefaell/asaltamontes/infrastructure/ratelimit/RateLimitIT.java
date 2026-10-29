package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@TestPropertySource(properties = {
        "asaltamontes.rate-limit.enabled=true",
        "asaltamontes.rate-limit.event-edits-per-hour=2",
        "asaltamontes.rate-limit.groups-created-per-day=2",
        "asaltamontes.rate-limit.group-member-adds-per-hour=2",
        "asaltamontes.rate-limit.follows-per-hour=3",
        "asaltamontes.rate-limit.community-messages-per-minute=2",
        "asaltamontes.rate-limit.event-messages-per-minute=2",
        "asaltamontes.rate-limit.posts-per-day=2"})
class RateLimitIT extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private io.github.yagogefaell.asaltamontes.message.service.MessageService messageService;
    @org.springframework.beans.factory.annotation.Autowired
    private io.github.yagogefaell.asaltamontes.event.service.EventService eventService;

    @Test
    void sixthLoginInAMinuteFromTheSameIp_isRejectedWith429() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(testData.uniqueName("nobody"), "wrong-password").with(from(ip)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(testData.uniqueName("nobody"), "wrong-password").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void sixthLoginInAMinuteForTheSameUsername_isRejectedEvenFromDifferentIps() throws Exception {
        UserAccount user = testData.createUser();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(user.getUsername(), "wrong-password").with(from("10.0.1." + i)))
                    .andExpect(status().isUnauthorized());
        }

        // Even with the right password: the account is locked for the rest of the minute
        mockMvc.perform(login(user.getUsername().toUpperCase(), TestDataFactory.DEFAULT_PASSWORD).with(from("10.0.1.99")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginsBelowTheLimit_stillWork() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(login(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD).with(from("10.0.2.1")))
                .andExpect(status().isOk());
    }

    @Test
    void fourthRegistrationInAMinuteFromTheSameIp_isRejected() throws Exception {
        String ip = "10.0.3.1";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(register().with(from(ip))).andExpect(status().isCreated());
        }

        mockMvc.perform(register().with(from(ip))).andExpect(status().isTooManyRequests());
    }

    @Test
    void markingNotificationsAsRead_isLimitedPerIp() throws Exception {
        UserAccount user = testData.createUser();
        String ip = "10.0.4.1";
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(post("/api/notifications/read").contentType(MediaType.APPLICATION_JSON).content("{\"all\":true}")
                    .with(auth.bearer(user)).with(from(ip))).andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/notifications/read").contentType(MediaType.APPLICATION_JSON).content("{\"all\":true}")
                .with(auth.bearer(user)).with(from(ip))).andExpect(status().isTooManyRequests());
    }

    @Test
    void editingEvents_isLimitedPerUser_withRetryAfter() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String missing = "/api/events/" + java.util.UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(patch(missing).with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(patch(missing).with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
        // Another user is not affected
        mockMvc.perform(patch(missing).with(auth.bearer(bea)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingGroups_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        for (int i = 0; i < 2; i++) {
            createGroup(ana, bea).andExpect(status().isOk());
        }

        createGroup(ana, bea).andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
        createGroup(bea, ana).andExpect(status().isOk());
    }

    @Test
    void addingAndExpellingTheSameMember_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        java.util.UUID group = testData.createGroup(ana, testData.createUser());
        String body = json(java.util.Map.of("usernames", java.util.List.of(bea.getUsername())));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/conversations/" + group + "/participants").with(auth.bearer(ana))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/conversations/" + group + "/participants/" + bea.getUsername()).with(auth.bearer(ana)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/conversations/" + group + "/participants").with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
    }

    @Test
    void following_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/users/follow/{u}", testData.createUser().getUsername()).with(auth.bearer(ana)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/users/follow/{u}", testData.createUser().getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
    }

    @Test
    void writingInACommunityChat_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        var community = testData.createCommunity(ana);
        mockMvc.perform(post("/api/communities/{id}/join", community.getPublicId()).with(auth.bearer(bea))).andExpect(status().isCreated());
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/communities/{id}/chat", community.getPublicId()).with(auth.bearer(ana)))
                .andReturn().getResponse().getContentAsString();
        java.util.UUID chat = java.util.UUID.fromString(objectMapper.readTree(body).get("conversationId").asText());
        messageService.processIncomingMessage(chat, ana.getId(), "uno");
        messageService.processIncomingMessage(chat, ana.getId(), "dos");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.processIncomingMessage(chat, ana.getId(), "tres"))
                .isInstanceOf(io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException.class);
        // Another member is not affected, and manual groups are not limited
        messageService.processIncomingMessage(chat, bea.getId(), "hola");
        java.util.UUID group = testData.createGroup(ana, bea);
        for (int i = 0; i < 4; i++) {
            messageService.processIncomingMessage(group, ana.getId(), "m" + i);
        }
    }

    @Test
    void writingInAnEventChat_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        java.util.UUID event = eventService.create(new io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto(
                testData.uniqueName("Quedada"), null, io.github.yagogefaell.asaltamontes.event.domain.EventType.MEETUP,
                java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS), null, "Europe/Madrid", "Parking", null, null,
                "Vigo", null, null, null, null, io.github.yagogefaell.asaltamontes.event.domain.EventVisibility.PUBLIC, null), ana).id();
        mockMvc.perform(post("/api/events/{id}/join", event).with(auth.bearer(bea))).andExpect(status().isOk());
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/events/{id}/chat", event).with(auth.bearer(ana)))
                .andReturn().getResponse().getContentAsString();
        java.util.UUID chat = java.util.UUID.fromString(objectMapper.readTree(body).get("conversationId").asText());
        messageService.processIncomingMessage(chat, ana.getId(), "uno");
        messageService.processIncomingMessage(chat, ana.getId(), "dos");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.processIncomingMessage(chat, ana.getId(), "tres"))
                .isInstanceOf(io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException.class);
        messageService.processIncomingMessage(chat, bea.getId(), "hola");
    }

    @Test
    void posting_isLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        for (int i = 0; i < 2; i++) {
            publish(ana).andExpect(status().isCreated());
        }

        publish(ana).andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
        publish(bea).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions publish(UserAccount by) throws Exception {
        return mockMvc.perform(post("/api/posts").with(auth.bearer(by)).contentType(MediaType.APPLICATION_JSON)
                .content(json(java.util.Map.of("body", "hola"))));
    }

    private org.springframework.test.web.servlet.ResultActions createGroup(UserAccount by, UserAccount member) throws Exception {
        return mockMvc.perform(post("/api/conversations/group").with(auth.bearer(by)).contentType(MediaType.APPLICATION_JSON)
                .content(json(java.util.Map.of("groupName", "g", "users", java.util.List.of(member.getUsername())))));
    }

    private MockHttpServletRequestBuilder login(String username, String password) throws Exception {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(username, password)));
    }

    private MockHttpServletRequestBuilder register() throws Exception {
        String username = testData.uniqueName("reg");
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(
                new RegisterRequest("Nueva Usuaria", username, username + "@test.local", "password123", "password123")));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
