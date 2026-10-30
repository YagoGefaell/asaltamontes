package io.github.yagogefaell.asaltamontes.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;

/**
 * T23a (BUG-034, D-025): a private profile is private. Following it is a request its owner approves; until then the
 * header is visible but not the lists, the events she organizes, her communities or her posts.
 */
class PrivateProfileIT extends AbstractIntegrationTest {

    private static final Instant START = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired private UserProfileRepository profiles;
    @Autowired private JdbcTemplate jdbc;

    // ---------------------------------------------------------------- following

    @Test
    void followingAPublicProfile_isImmediate_andAPrivateOneCreatesARequest() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount open = testData.createUser();
        UserAccount secret = testData.createPrivateUser();

        follow(ana, open).andExpect(status().isOk());
        follow(ana, secret).andExpect(status().isAccepted());

        assertThat(followers(open)).isEqualTo(1);
        assertThat(followers(secret)).isZero();
        assertThat(following(ana)).isEqualTo(1);
        profile(ana, secret).andExpect(jsonPath("$.isFollowing").value(false))
                .andExpect(jsonPath("$.isFollowRequested").value(true))
                .andExpect(jsonPath("$.canViewContent").value(false));
        profile(ana, open).andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.isFollowRequested").value(false))
                .andExpect(jsonPath("$.canViewContent").value(true));
    }

    @Test
    void askingTwice_isIdempotent_andNotifiesTheOwnerOnce() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();

        follow(ana, secret).andExpect(status().isAccepted());
        follow(ana, secret).andExpect(status().isAccepted());

        assertThat(requestCount(secret)).isEqualTo(1);
        awaitNotification(secret, "FOLLOW_REQUEST", 1);
        // No NEW_FOLLOWER: nothing was followed
        assertThat(notificationCount(secret, "NEW_FOLLOWER")).isZero();
    }

    @Test
    void unfollow_cancelsAPendingRequest() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();
        follow(ana, secret).andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/users/follow/{u}", secret.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());

        assertThat(requestCount(secret)).isZero();
        profile(ana, secret).andExpect(jsonPath("$.isFollowRequested").value(false));
        inbox(secret).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void aBlockedPair_cannotRequest_andABlockDropsThePendingRequest() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();
        UserAccount cris = testData.createUser();
        follow(ana, secret).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/users/{u}/block", ana.getUsername()).with(auth.bearer(secret)))
                .andExpect(status().isNoContent());

        assertThat(requestCount(secret)).isZero();
        follow(ana, secret).andExpect(status().isNotFound());

        // Blocking by the requester drops it too
        follow(cris, secret).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/users/{u}/block", secret.getUsername()).with(auth.bearer(cris)))
                .andExpect(status().isNoContent());
        assertThat(requestCount(secret)).isZero();
    }

    // ---------------------------------------------------------------- the inbox

    @Test
    void theInbox_isPaginated_newestFirst_andOnlyTheOwners() throws Exception {
        UserAccount secret = testData.createPrivateUser();
        UserAccount other = testData.createPrivateUser();
        UserAccount a = testData.createUser();
        UserAccount b = testData.createUser();
        UserAccount c = testData.createUser();
        follow(a, secret).andExpect(status().isAccepted());
        follow(b, secret).andExpect(status().isAccepted());
        follow(c, secret).andExpect(status().isAccepted());
        follow(a, other).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/users/me/follow-requests").with(auth.bearer(secret)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(contains(c.getUsername(), b.getUsername())))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
        inbox(other).andExpect(jsonPath("$.content[*].username").value(contains(a.getUsername())));
    }

    @Test
    void approve_makesHerAFollower_countsOnce_andNotifiesHer() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();
        follow(ana, secret).andExpect(status().isAccepted());

        decide(secret, ana, "approve").andExpect(status().isNoContent());
        // A second approval (or a click on a stale screen) finds nothing and counts nothing
        decide(secret, ana, "approve").andExpect(status().isNotFound());

        assertThat(followers(secret)).isEqualTo(1);
        assertThat(following(ana)).isEqualTo(1);
        profile(ana, secret).andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.isFollowRequested").value(false))
                .andExpect(jsonPath("$.canViewContent").value(true));
        inbox(secret).andExpect(jsonPath("$.content.length()").value(0));
        awaitNotification(ana, "FOLLOW_REQUEST_APPROVED", 1);
    }

    @Test
    void reject_removesTheRequest_followsNoOne_andSaysNothing() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();
        follow(ana, secret).andExpect(status().isAccepted());

        decide(secret, ana, "reject").andExpect(status().isNoContent());
        decide(secret, ana, "reject").andExpect(status().isNotFound());

        assertThat(followers(secret)).isZero();
        profile(ana, secret).andExpect(jsonPath("$.isFollowRequested").value(false))
                .andExpect(jsonPath("$.canViewContent").value(false));
        // She may ask again
        follow(ana, secret).andExpect(status().isAccepted());
        assertThat(requestCount(secret)).isEqualTo(1);
        assertThat(notificationCount(ana, "FOLLOW_REQUEST_APPROVED")).isZero();
    }

    @Test
    void onlyTheOwnerDecidesHerRequests() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount secret = testData.createPrivateUser();
        UserAccount intruder = testData.createUser();
        follow(ana, secret).andExpect(status().isAccepted());

        // The path is always "my" requests: an unrelated user has none from ana
        decide(intruder, ana, "approve").andExpect(status().isNotFound());
        decide(secret, intruder, "approve").andExpect(status().isNotFound());
        decide(secret, ana, "approve").andExpect(status().isNoContent());
        assertThat(followers(intruder)).isZero();
        mockMvc.perform(post("/api/users/me/follow-requests/{u}/approve", ana.getUsername()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- what a private profile hides

    @Test
    void withoutApproval_listsEventsCommunitiesAndPostsAreHidden_butTheHeaderIsNot() throws Exception {
        UserAccount secret = testData.createPrivateUser();
        UserAccount friend = testData.createUser();
        UserAccount stranger = testData.createUser();
        UserAccount requester = testData.createUser();
        // Her relations, her community, an event she organizes and a post on her profile
        follow(friend, secret).andExpect(status().isAccepted());
        decide(secret, friend, "approve").andExpect(status().isNoContent());
        follow(secret, friend).andExpect(status().isOk());
        Community community = testData.createCommunity(secret);
        String tag = testData.uniqueName("priv");
        mockMvc.perform(post("/api/events").with(auth.bearer(secret)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(event(tag)))).andExpect(status().isCreated());
        String postId = objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(secret))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "Solo para ellas"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        follow(requester, secret).andExpect(status().isAccepted());

        for (UserAccount nobody : List.of(stranger, requester)) {
            profile(nobody, secret).andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(secret.getUsername()))
                    .andExpect(jsonPath("$.fullName").value("Test User"))
                    .andExpect(jsonPath("$.followersCount").value(1))
                    .andExpect(jsonPath("$.followingCount").value(1))
                    .andExpect(jsonPath("$.isPublicProfile").value(false))
                    .andExpect(jsonPath("$.canViewContent").value(false));
            empty(nobody, "/api/users/followers/" + secret.getUsername());
            empty(nobody, "/api/users/following/" + secret.getUsername());
            empty(nobody, "/api/users/" + secret.getUsername() + "/events");
            empty(nobody, "/api/users/" + secret.getUsername() + "/communities");
            mockMvc.perform(get("/api/users/{u}/posts", secret.getUsername()).with(auth.bearer(nobody)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
            mockMvc.perform(get("/api/posts/{id}", postId).with(auth.bearer(nobody))).andExpect(status().isNotFound());
        }

        // The approved follower and the owner see all of it
        for (UserAccount viewer : List.of(friend, secret)) {
            mockMvc.perform(get("/api/users/followers/{u}", secret.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(jsonPath("$.content[0].username").value(friend.getUsername()));
            mockMvc.perform(get("/api/users/following/{u}", secret.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(jsonPath("$.content[0].username").value(friend.getUsername()));
            mockMvc.perform(get("/api/users/{u}/events", secret.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(jsonPath("$.content.length()").value(1));
            mockMvc.perform(get("/api/users/{u}/communities", secret.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(jsonPath("$.content[0].slug").value(community.getSlug()));
            mockMvc.perform(get("/api/users/{u}/posts", secret.getUsername()).with(auth.bearer(viewer)))
                    .andExpect(jsonPath("$.items.length()").value(1));
            mockMvc.perform(get("/api/posts/{id}", postId).with(auth.bearer(viewer))).andExpect(status().isOk());
        }
    }

    @Test
    void aPrivatePostStaysOutOfTheHomeFeedAndExploreOfNonFollowers() throws Exception {
        UserAccount secret = testData.createPrivateUser();
        UserAccount stranger = testData.createUser();
        UserAccount follower = testData.createUser();
        follow(follower, secret).andExpect(status().isAccepted());
        decide(secret, follower, "approve").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/posts").with(auth.bearer(secret)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("body", "Solo seguidoras")))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/feed").with(auth.bearer(follower))).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/feed").with(auth.bearer(stranger))).andExpect(jsonPath("$.items.length()").value(0));
        // The database is shared with other tests: look only for her posts
        mockMvc.perform(get("/api/explore/posts").param("limit", "50").with(auth.bearer(stranger)))
                .andExpect(jsonPath("$.items[?(@.author.username == '" + secret.getUsername() + "')]").isEmpty());
    }

    // ---------------------------------------------------------------- changing the privacy

    @Test
    void turningPublic_acceptsEveryPendingRequest_andCountsThem() throws Exception {
        UserAccount secret = testData.createPrivateUser();
        UserAccount a = testData.createUser();
        UserAccount b = testData.createUser();
        follow(a, secret).andExpect(status().isAccepted());
        follow(b, secret).andExpect(status().isAccepted());

        setPublic(secret, true);

        assertThat(requestCount(secret)).isZero();
        assertThat(followers(secret)).isEqualTo(2);
        assertThat(following(a)).isEqualTo(1);
        assertThat(following(b)).isEqualTo(1);
        profile(a, secret).andExpect(jsonPath("$.isFollowing").value(true)).andExpect(jsonPath("$.canViewContent").value(true));
        awaitNotification(a, "FOLLOW_REQUEST_APPROVED", 1);
        // Already public: following is immediate again
        follow(testData.createUser(), secret).andExpect(status().isOk());
    }

    @Test
    void turningPrivate_keepsTheCurrentFollowers_andNewOnesNeedApproval() throws Exception {
        UserAccount open = testData.createUser();
        UserAccount old = testData.createUser();
        UserAccount fresh = testData.createUser();
        follow(old, open).andExpect(status().isOk());

        setPublic(open, false);

        assertThat(followers(open)).isEqualTo(1);
        profile(old, open).andExpect(jsonPath("$.isFollowing").value(true)).andExpect(jsonPath("$.canViewContent").value(true));
        follow(fresh, open).andExpect(status().isAccepted());
        profile(fresh, open).andExpect(jsonPath("$.canViewContent").value(false));
    }

    @Test
    void unfollowingAPrivateProfile_losesTheAccessAtOnce() throws Exception {
        UserAccount secret = testData.createPrivateUser();
        UserAccount ana = testData.createUser();
        follow(ana, secret).andExpect(status().isAccepted());
        decide(secret, ana, "approve").andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/users/follow/{u}", secret.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());

        profile(ana, secret).andExpect(jsonPath("$.canViewContent").value(false));
        empty(ana, "/api/users/followers/" + secret.getUsername());
        assertThat(followers(secret)).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions follow(UserAccount who, UserAccount target) throws Exception {
        return mockMvc.perform(post("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who)));
    }

    private ResultActions decide(UserAccount owner, UserAccount requester, String action) throws Exception {
        return mockMvc.perform(post("/api/users/me/follow-requests/{u}/{a}", requester.getUsername(), action)
                .with(auth.bearer(owner)));
    }

    private ResultActions inbox(UserAccount owner) throws Exception {
        return mockMvc.perform(get("/api/users/me/follow-requests").with(auth.bearer(owner))).andExpect(status().isOk());
    }

    private ResultActions profile(UserAccount viewer, UserAccount target) throws Exception {
        return mockMvc.perform(get("/api/users/{u}", target.getUsername()).with(auth.bearer(viewer))).andExpect(status().isOk());
    }

    private void empty(UserAccount viewer, String url) throws Exception {
        mockMvc.perform(get(url).with(auth.bearer(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0)).andExpect(jsonPath("$.totalElements").value(0));
    }

    private void setPublic(UserAccount user, boolean value) throws Exception {
        mockMvc.perform(patch("/api/users/me").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("isPublicProfile", value))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isPublicProfile").value(value));
    }

    private Map<String, Object> event(String tag) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Quedada " + tag);
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", START.toString());
        body.put("meetingPointName", "Punto de encuentro");
        body.put("city", tag);
        return body;
    }

    private long followers(UserAccount u) {
        return profiles.findByUserId(u.getId()).orElseThrow().getFollowersCount();
    }

    private long following(UserAccount u) {
        return profiles.findByUserId(u.getId()).orElseThrow().getFollowingCount();
    }

    private int requestCount(UserAccount owner) {
        return jdbc.queryForObject("SELECT count(*) FROM follow_requests WHERE followed_id = ?", Integer.class, owner.getId());
    }

    private int notificationCount(UserAccount recipient, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ?",
                Integer.class, recipient.getId(), type);
    }

    private void awaitNotification(UserAccount recipient, String type, int expected) {
        await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notificationCount(recipient, type)).isEqualTo(expected));
    }
}
