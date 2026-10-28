package io.github.yagogefaell.asaltamontes.user.controller;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.infrastructure.config.AsyncConfig;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import io.github.yagogefaell.asaltamontes.user.service.FollowService;
import jakarta.persistence.EntityManagerFactory;

/** T31b (D-023): another user's events and communities, the "followed by me" filter and the follow suggestions. */
class ProfileAndHomeIT extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired private CommunityService communityService;
    @Autowired private BlockService blocks;
    @Autowired private FollowService follows;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired @Qualifier(AsyncConfig.NOTIFICATIONS_EXECUTOR) private ThreadPoolTaskExecutor notificationExecutor;

    // ---- GET /api/users/{username}/events ----

    @Test
    void userEvents_showOnlyWhatTheViewerCanSee_andNeverAttendance() throws Exception {
        UserAccount bea = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        UserAccount banned = testData.createUser();
        Community community = testData.createCommunity(bea);
        communityService.joinCommunity(community.getPublicId(), member);
        communityService.joinCommunity(community.getPublicId(), banned);
        jdbc.update("UPDATE community_members SET status = 'BANNED' WHERE user_account_id = ?", banned.getId());

        String tag = testData.uniqueName("perfil");
        create(bea, ev("publica", tag, 0, Map.of()));
        create(bea, ev("solo-miembros", tag, 1, Map.of("communityId", community.getPublicId(), "visibility", "COMMUNITY")));
        UUID cancelled = idOf(create(bea, ev("cancelada", tag, 2, Map.of())));
        mockMvc.perform(post("/api/events/" + cancelled + "/cancel").with(auth.bearer(bea))).andExpect(status().isOk());
        // Where bea is going is somebody else's event: it must not show on her profile
        UserAccount cris = testData.createUser();
        UUID crisEvent = idOf(create(cris, ev("de-cris", tag, 3, Map.of())));
        mockMvc.perform(post("/api/events/" + crisEvent + "/join").with(auth.bearer(bea))).andExpect(status().isOk());

        userEvents(member, bea).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "publica"), t(tag, "solo-miembros"))));
        userEvents(bea, bea).andExpect(jsonPath("$.content[*].title")
                .value(contains(t(tag, "publica"), t(tag, "solo-miembros"))));
        userEvents(outsider, bea).andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "publica"))));
        userEvents(banned, bea).andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "publica"))));
        userEvents(outsider, bea).andExpect(jsonPath("$.content[*].title").value(not(hasItem(t(tag, "de-cris")))));
        // cris' own profile shows her event, and nothing of the others
        userEvents(outsider, cris).andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "de-cris"))));
    }

    @Test
    void userEvents_arePaginated_andBlocksAndUnknownUsersAreTheSame404() throws Exception {
        UserAccount bea = testData.createUser();
        UserAccount viewer = testData.createUser();
        UserAccount blockedByViewer = testData.createUser();
        UserAccount blockerOfViewer = testData.createUser();
        String tag = testData.uniqueName("pag");
        for (int i = 0; i < 3; i++) {
            create(bea, ev("e" + i, tag, i, Map.of()));
        }
        mockMvc.perform(get("/api/users/{u}/events", bea.getUsername()).with(auth.bearer(viewer)).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "e2"))))
                .andExpect(jsonPath("$.totalElements").value(3));

        create(blockedByViewer, ev("x", tag, 0, Map.of()));
        create(blockerOfViewer, ev("y", tag, 0, Map.of()));
        blocks.block(viewer.getId(), blockedByViewer.getUsername());
        blocks.block(blockerOfViewer.getId(), viewer.getUsername());

        String unknown = mockMvc.perform(get("/api/users/{u}/events", "nobody_" + tag).with(auth.bearer(viewer)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (UserAccount other : new UserAccount[] {blockedByViewer, blockerOfViewer}) {
            String body = mockMvc.perform(get("/api/users/{u}/events", other.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertEquals(strip(unknown), strip(body).replace(other.getUsername(), "nobody_" + tag));
            mockMvc.perform(get("/api/users/{u}/communities", other.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(status().isNotFound());
        }
    }

    // ---- GET /api/users/{username}/communities ----

    @Test
    void userCommunities_listOnlyPublicOnes_withTheViewersRole() throws Exception {
        UserAccount bea = testData.createUser();
        UserAccount viewer = testData.createUser();
        Community open = testData.createCommunity(bea);
        Community secret = communityService.create(new CreateCommunityDto(testData.uniqueName("privada"), "x", null, null,
                CommunityVisibility.PRIVATE, null, null), bea);
        Community banishedFrom = testData.createCommunity(testData.createUser());
        communityService.joinCommunity(banishedFrom.getPublicId(), bea);
        jdbc.update("UPDATE community_members SET status = 'BANNED' WHERE user_account_id = ? AND community_id = ?",
                bea.getId(), banishedFrom.getId());
        communityService.joinCommunity(open.getPublicId(), viewer);

        mockMvc.perform(get("/api/users/{u}/communities", bea.getUsername()).with(auth.bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug").value(contains(open.getSlug())))
                .andExpect(jsonPath("$.content[0].role").value("MEMBER")) // the viewer's, not bea's OWNER
                .andExpect(jsonPath("$.totalElements").value(1));
        // The private one is hidden from everybody, bea herself included in this endpoint
        mockMvc.perform(get("/api/users/{u}/communities", bea.getUsername()).with(auth.bearer(testData.createUser())))
                .andExpect(jsonPath("$.content[*].slug").value(not(hasItem(secret.getSlug()))))
                .andExpect(jsonPath("$.content[0].role").doesNotExist());
        mockMvc.perform(get("/api/users/{u}/communities", bea.getUsername()).with(auth.bearer(viewer)).param("sort", "password"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /api/events?followedBy=me ----

    @Test
    void followedBy_listsOnlyFollowedOrganizers_withTheSameVisibilityRules() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount followed = testData.createUser();
        UserAccount stranger = testData.createUser();
        UserAccount blockedLater = testData.createUser();
        Community community = testData.createCommunity(followed);
        follows.followUser(me.getId(), followed.getUsername());
        follows.followUser(me.getId(), blockedLater.getUsername());

        String tag = testData.uniqueName("inicio");
        create(followed, ev("publica", tag, 0, Map.of()));
        create(followed, ev("solo-miembros", tag, 1, Map.of("communityId", community.getPublicId(), "visibility", "COMMUNITY")));
        create(stranger, ev("de-otra", tag, 2, Map.of()));
        create(me, ev("mia", tag, 3, Map.of()));
        create(blockedLater, ev("bloqueada", tag, 4, Map.of()));
        blocks.block(me.getId(), blockedLater.getUsername());

        mockMvc.perform(get("/api/events").with(auth.bearer(me)).param("followedBy", "me").param("city", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "publica"))))
                .andExpect(jsonPath("$.totalElements").value(1));
        // Once she is a member the community event shows too
        communityService.joinCommunity(community.getPublicId(), me);
        mockMvc.perform(get("/api/events").with(auth.bearer(me)).param("followedBy", "me").param("city", tag))
                .andExpect(jsonPath("$.content[*].title").value(contains(t(tag, "publica"), t(tag, "solo-miembros"))));
        mockMvc.perform(get("/api/events").with(auth.bearer(me)).param("followedBy", "all")).andExpect(status().isBadRequest());
    }

    // ---- GET /api/users/suggestions ----

    @Test
    void suggestions_areFriendsOfFriendsAndCommunityMates_withoutTheExcluded() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount friend = testData.createUser();
        UserAccount friendOfFriend = testData.createUser();
        UserAccount alreadyFollowed = testData.createUser();
        UserAccount mate = testData.createUser();
        UserAccount bannedMate = testData.createUser();
        UserAccount blockedFof = testData.createUser();
        UserAccount blockerFof = testData.createUser();
        UserAccount nobody = testData.createUser();
        follows.followUser(me.getId(), friend.getUsername());
        follows.followUser(me.getId(), alreadyFollowed.getUsername());
        follows.followUser(friend.getId(), friendOfFriend.getUsername());
        follows.followUser(friend.getId(), alreadyFollowed.getUsername());
        follows.followUser(friend.getId(), me.getUsername());
        follows.followUser(friend.getId(), blockedFof.getUsername());
        follows.followUser(friend.getId(), blockerFof.getUsername());
        blocks.block(me.getId(), blockedFof.getUsername());
        blocks.block(blockerFof.getId(), me.getUsername());
        Community community = testData.createCommunity(mate);
        communityService.joinCommunity(community.getPublicId(), me);
        communityService.joinCommunity(community.getPublicId(), bannedMate);
        jdbc.update("UPDATE community_members SET status = 'BANNED' WHERE user_account_id = ?", bannedMate.getId());

        mockMvc.perform(get("/api/users/suggestions").with(auth.bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username")
                        .value(containsInAnyOrder(friendOfFriend.getUsername(), mate.getUsername())))
                .andExpect(jsonPath("$.content[0].isFollowing").value(false))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/users/suggestions").with(auth.bearer(nobody)))
                .andExpect(jsonPath("$.content").value(empty()));
        mockMvc.perform(get("/api/users/suggestions").with(auth.bearer(me)).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        // Following her removes her from the list
        follows.followUser(me.getId(), mate.getUsername());
        mockMvc.perform(get("/api/users/suggestions").with(auth.bearer(me)))
                .andExpect(jsonPath("$.content[*].username").value(contains(friendOfFriend.getUsername())));
    }

    @Test
    void suggestions_andListings_doNotRunOneQueryPerRow() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount friend = testData.createUser();
        follows.followUser(me.getId(), friend.getUsername());
        String tag = testData.uniqueName("nplus1");
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        long few = 0;
        for (int round = 0; round < 2; round++) {
            int rows = round == 0 ? 2 : 8;
            for (int i = 0; i < rows; i++) {
                UserAccount other = testData.createUser();
                follows.followUser(friend.getId(), other.getUsername());
                Community c = testData.createCommunity(other);
                communityService.joinCommunity(c.getPublicId(), friend);
                create(other, ev("e" + i, tag, i, Map.of()));
                follows.followUser(me.getId(), other.getUsername());
            }
            // The setup above fires async notification tasks (NotificationListener) that run their own SQL; if they
            // are still running when the counter resets they leak into the measured window (BUG-077)
            awaitNotificationsIdle();
            stats.clear();
            mockMvc.perform(get("/api/users/suggestions").with(auth.bearer(me)).param("size", "50")).andExpect(status().isOk());
            mockMvc.perform(get("/api/users/{u}/communities", friend.getUsername()).with(auth.bearer(me)).param("size", "50"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/users/{u}/events", friend.getUsername()).with(auth.bearer(me)).param("size", "50"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/events").with(auth.bearer(me)).param("followedBy", "me").param("size", "50"))
                    .andExpect(status().isOk());
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

    // ---- helpers ----

    private void awaitNotificationsIdle() {
        var pool = notificationExecutor.getThreadPoolExecutor();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                .until(() -> pool.getActiveCount() == 0 && pool.getQueue().isEmpty());
    }

    private static String t(String tag, String name) {
        return name + " " + tag;
    }

    private static String strip(String body) {
        return body.replaceAll("\"(timestamp|requestId)\"\\s*:\\s*\"[^\"]*\"", "");
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

    private ResultActions userEvents(UserAccount viewer, UserAccount target) throws Exception {
        return mockMvc.perform(get("/api/users/{u}/events", target.getUsername()).with(auth.bearer(viewer)));
    }

    private UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }
}
