package io.github.yagogefaell.asaltamontes.community.controller;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.infrastructure.config.AsyncConfig;
import io.github.yagogefaell.asaltamontes.message.service.CommunityChatService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import jakarta.persistence.EntityManagerFactory;

/** T31c: GET /api/communities/me (chat, unread, next meetup) and GET /api/communities/me/pending. */
class MyCommunitiesIT extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired private CommunityService communityService;
    @Autowired private CommunityChatService communityChat;
    @Autowired private MessageService messages;
    @Autowired private BlockService blocks;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired @Qualifier(AsyncConfig.NOTIFICATIONS_EXECUTOR) private ThreadPoolTaskExecutor notificationExecutor;

    @Test
    void myCommunities_showLastMessage_unread_andNextEvent_byActivity() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount bea = testData.createUser();
        Community quiet = testData.createCommunity(me);
        Community busy = testData.createCommunity(bea);
        communityService.joinCommunity(busy.getPublicId(), me);
        Community third = testData.createCommunity(bea);
        communityService.joinCommunity(third.getPublicId(), me);

        say(busy, bea, "hola cabras");
        say(busy, bea, "quedamos el sabado");
        // next meetup: the closest of the scheduled ones, not the cancelled one nor a later one
        createEvent(bea, busy, "tarde", 5);
        UUID soon = createEvent(bea, busy, "pronto", 2);
        UUID cancelled = createEvent(bea, busy, "cancelada", 1);
        mockMvc.perform(post("/api/events/" + cancelled + "/cancel").with(auth.bearer(bea))).andExpect(status().isOk());

        mockMvc.perform(get("/api/communities/me").with(auth.bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value(busy.getSlug()))
                .andExpect(jsonPath("$.content[0].chat.lastMessage").value("quedamos el sabado"))
                .andExpect(jsonPath("$.content[0].chat.lastSender").value(bea.getUsername()))
                .andExpect(jsonPath("$.content[0].chat.unreadCount").value(2))
                .andExpect(jsonPath("$.content[0].nextEvent.id").value(soon.toString()))
                .andExpect(jsonPath("$.content[0].role").value("MEMBER"))
                // same fields as before, none of the internal ids
                .andExpect(jsonPath("$.content[0].communityInternalId").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(busy.getPublicId().toString()))
                .andExpect(jsonPath("$.content[2].slug").value(quiet.getSlug()))
                .andExpect(jsonPath("$.content[2].nextEvent").doesNotExist())
                .andExpect(jsonPath("$.content[2].chat.unreadCount").value(0));
    }

    @Test
    void aMessageFromABlockedUser_isNotShownInThePreview_andBannedCommunitiesAreNotListed() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(bea);
        communityService.joinCommunity(community.getPublicId(), me);
        say(community, bea, "secreto de bea");
        Community banishedFrom = testData.createCommunity(bea);
        communityService.joinCommunity(banishedFrom.getPublicId(), me);
        mockMvc.perform(post("/api/communities/" + banishedFrom.getPublicId() + "/members/" + me.getUsername() + "/ban")
                .with(auth.bearer(bea)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"spam\"}"))
                .andExpect(status().is2xxSuccessful());

        blocks.block(me.getId(), bea.getUsername());

        mockMvc.perform(get("/api/communities/me").with(auth.bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug=='" + community.getSlug() + "')].chat.lastMessage").value(contains((Object) null)))
                .andExpect(jsonPath("$.content[?(@.slug=='" + community.getSlug() + "')].chat.lastSender").value(contains((Object) null)))
                .andExpect(jsonPath("$.content[?(@.slug=='" + community.getSlug() + "')].chat.lastMessageAt").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.slug=='" + banishedFrom.getSlug() + "')]").isEmpty());
    }

    @Test
    void myCommunities_doNotRunOneQueryPerRow() throws Exception {
        UserAccount me = testData.createUser();
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        long few = 0;
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < (round == 0 ? 2 : 8); i++) {
                UserAccount owner = testData.createUser();
                Community c = testData.createCommunity(owner);
                communityService.joinCommunity(c.getPublicId(), me);
                say(c, owner, "m" + i);
                createEvent(owner, c, "e" + i, i + 1);
            }
            // the setup fires async notification tasks with their own SQL: wait so they do not leak into the count
            awaitNotificationsIdle();
            stats.clear();
            mockMvc.perform(get("/api/communities/me").with(auth.bearer(me)).param("size", "50")).andExpect(status().isOk());
            mockMvc.perform(get("/api/communities/me/pending").with(auth.bearer(me))).andExpect(status().isOk());
            awaitNotificationsIdle();
            long statements = stats.getPrepareStatementCount();
            if (round == 0) {
                few = statements;
            } else {
                assertTrue(statements <= few, "4x the rows must not mean more statements: " + few + " -> " + statements);
            }
        }
        stats.setStatisticsEnabled(false);
    }

    @Test
    void pending_groupsRequestsForModerators_andOwnRequests_respectingRolesAndBlocks() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount blocked = testData.createUser();
        Community secret = communityService.create(new CreateCommunityDto(testData.uniqueName("privada"), "x", null, null,
                CommunityVisibility.PRIVATE, null, null), owner);
        communityService.joinCommunity(secret.getPublicId(), mod);
        communityService.joinCommunity(secret.getPublicId(), member);
        communityService.joinCommunity(secret.getPublicId(), ana);
        communityService.joinCommunity(secret.getPublicId(), bea);
        communityService.joinCommunity(secret.getPublicId(), blocked);
        // mod and member come in through the request flow; promote mod
        mockMvc.perform(post("/api/communities/" + secret.getPublicId() + "/requests/" + mod.getUsername() + "/approve")
                .with(auth.bearer(owner))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/communities/" + secret.getPublicId() + "/requests/" + member.getUsername() + "/approve")
                .with(auth.bearer(owner))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(put("/api/communities/" + secret.getPublicId() + "/members/" + mod.getUsername() + "/role")
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().is2xxSuccessful());
        blocks.block(mod.getId(), blocked.getUsername());

        // owner: 3 pending (ana, bea, blocked); the moderator does not count the one she blocked
        mockMvc.perform(get("/api/communities/me/pending").with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toApprove[0].community.slug").value(secret.getSlug()))
                .andExpect(jsonPath("$.toApprove[0].pendingCount").value(3))
                .andExpect(jsonPath("$.mine").isEmpty());
        mockMvc.perform(get("/api/communities/me/pending").with(auth.bearer(mod)))
                .andExpect(jsonPath("$.toApprove[0].pendingCount").value(2));
        // a plain member sees nothing to approve; an applicant sees only her own request
        mockMvc.perform(get("/api/communities/me/pending").with(auth.bearer(member)))
                .andExpect(jsonPath("$.toApprove").isEmpty())
                .andExpect(jsonPath("$.mine").isEmpty());
        mockMvc.perform(get("/api/communities/me/pending").with(auth.bearer(ana)))
                .andExpect(jsonPath("$.toApprove").isEmpty())
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].community.slug").value(secret.getSlug()))
                // the applicants' identities never show
                .andExpect(jsonPath("$.mine[0].username").doesNotExist());
    }

    // ---- helpers ----

    private void say(Community community, UserAccount sender, String text) {
        UUID chat = communityChat.open(community.getPublicId(), sender).conversationId();
        messages.processIncomingMessage(chat, sender.getId(), text);
    }

    private UUID createEvent(UserAccount organizer, Community community, String title, int dayOffset) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title + " " + testData.uniqueName("ev"));
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", BASE.plus(dayOffset, ChronoUnit.DAYS).toString());
        body.put("meetingPointName", "Punto");
        body.put("city", "Vigo");
        body.put("communityId", community.getPublicId());
        body.put("visibility", "COMMUNITY");
        String response = mockMvc.perform(post("/api/events").with(auth.bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void awaitNotificationsIdle() {
        var pool = notificationExecutor.getThreadPoolExecutor();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                .until(() -> pool.getActiveCount() == 0 && pool.getQueue().isEmpty());
    }
}
