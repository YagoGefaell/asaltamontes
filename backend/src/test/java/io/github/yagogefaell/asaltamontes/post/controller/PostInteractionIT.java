package io.github.yagogefaell.asaltamontes.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.persistence.EntityManagerFactory;

/** T22a2: likes, comments, the POST_COMMENT notification and the explore grid; D-025 interaction rules and D-026 moderation. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostInteractionIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    // ---------------------------------------------------------------- likes

    @Test
    void likeAndUnlike_areIdempotent_andTheCounterOnlyMovesWhenTheRowChanges() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(community, bea);
        String id = postIn(owner, community, "hola");

        JsonNode first = body(like(bea, id).andExpect(status().isOk()));
        assertThat(first.get("likedByMe").asBoolean()).isTrue();
        assertThat(first.get("likeCount").asLong()).isEqualTo(1);
        assertThat(body(like(bea, id)).get("likeCount").asLong()).isEqualTo(1);
        assertThat(likesRows(id)).isEqualTo(1);
        JsonNode seen = body(getPost(bea, id));
        assertThat(seen.get("likedByMe").asBoolean()).isTrue();
        assertThat(seen.get("likeCount").asLong()).isEqualTo(1);
        assertThat(body(getPost(owner, id)).get("likedByMe").asBoolean()).isFalse();

        assertThat(body(unlike(bea, id).andExpect(status().isOk())).get("likeCount").asLong()).isZero();
        assertThat(body(unlike(bea, id)).get("likeCount").asLong()).isZero();
        assertThat(counter(id, "like_count")).isZero();
        assertThat(body(getPost(bea, id)).get("likedByMe").asBoolean()).isFalse();
    }

    @Test
    void concurrentLikesFromManyUsersAndRepeatedFromOne_leaveTheCounterRight() throws Exception {
        UserAccount owner = testData.createUser();
        Community community = testData.createCommunity(owner);
        String id = postIn(owner, community, "popular");
        List<UserAccount> fans = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UserAccount fan = testData.createUser();
            join(community, fan);
            fans.add(fan);
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (UserAccount fan : fans) {
                for (int repeat = 0; repeat < 3; repeat++) {
                    Callable<Integer> call = () -> like(fan, id).andReturn().getResponse().getStatus();
                    results.add(pool.submit(call));
                }
            }
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(likesRows(id)).isEqualTo(6);
        assertThat(counter(id, "like_count")).isEqualTo(6);

        // Likes and unlikes racing for the same user end consistent too
        UserAccount racer = fans.get(0);
        pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                boolean liking = i % 2 == 0;
                Callable<Integer> call = () -> (liking ? like(racer, id) : unlike(racer, id)).andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(counter(id, "like_count")).isEqualTo(likesRows(id));
    }

    @Test
    void whoCanLike_membersAndTheMuted_notNonMembers_neverTheBannedOrTheBlocked() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount muted = testData.createUser();
        UserAccount banned = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(member, muted, banned)) {
            join(community, u);
        }
        String id = postIn(owner, community, "hola");
        moderate(owner, community, muted, "mute", null);
        moderate(owner, community, banned, "ban", "{\"reason\":\"Spam\"}");

        like(member, id).andExpect(status().isOk());
        like(muted, id).andExpect(status().isOk());
        like(outsider, id).andExpect(status().isForbidden());
        like(banned, id).andExpect(status().isNotFound());
        like(member, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assertThat(counter(id, "like_count")).isEqualTo(2);
        // The outsider can see it (public community) but not like
        getPost(outsider, id).andExpect(status().isOk());

        // A block in either direction hides the post, so there is nothing to like (also for the owner of the community)
        mockMvc.perform(post("/api/users/" + owner.getUsername() + "/block").with(auth.bearer(member))).andExpect(status().is2xxSuccessful());
        unlike(member, id).andExpect(status().isNotFound());
        UserAccount friend = testData.createUser();
        join(community, friend);
        mockMvc.perform(post("/api/users/" + friend.getUsername() + "/block").with(auth.bearer(owner))).andExpect(status().is2xxSuccessful());
        like(friend, id).andExpect(status().isNotFound());
    }

    @Test
    void likesOnAProfilePost_areForWhoeverCanSeeIt_andAPrivateCommunityOnlyForItsMembers() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount blocked = testData.createUser();
        String id = body(create(ana, null, "en mi perfil")).get("id").asText();

        like(bea, id).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(blocked))).andExpect(status().is2xxSuccessful());
        like(blocked, id).andExpect(status().isNotFound());

        UserAccount owner = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        String secret = postIn(owner, community, "secreto");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/communities/" + community.getPublicId())
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        like(outsider, secret).andExpect(status().isNotFound());
        like(owner, secret).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- comments

    @Test
    void comments_canBeWrittenListedOldestFirstAndPaged_withoutInternalIds() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(community, bea);
        String id = postIn(owner, community, "hola");

        List<String> written = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JsonNode created = body(comment(i % 2 == 0 ? bea : owner, id, "comentario " + i).andExpect(status().isCreated()));
            written.add(created.get("id").asText());
            assertThat(created.toString()).doesNotContain("\"postId\"", "\"authorId\"");
        }
        assertThat(counter(id, "comment_count")).isEqualTo(5);
        assertThat(body(getPost(bea, id)).get("commentCount").asLong()).isEqualTo(5);

        JsonNode page1 = body(comments(bea, id, null, 2).andExpect(status().isOk()));
        assertThat(ids(page1)).isEqualTo(written.subList(0, 2));
        JsonNode page2 = body(comments(bea, id, page1.get("nextBefore").asText(), 2));
        JsonNode page3 = body(comments(bea, id, page2.get("nextBefore").asText(), 2));
        assertThat(ids(page2)).isEqualTo(written.subList(2, 4));
        assertThat(ids(page3)).isEqualTo(written.subList(4, 5));
        assertThat(page3.get("nextBefore").isNull()).isTrue();
        assertThat(page1.get("items").get(0).get("author").get("username").asText()).isEqualTo(bea.getUsername());

        // A cursor of another list is a 400
        comments(bea, id, "bm90LWEtY3Vyc29y", 2).andExpect(status().isBadRequest());
    }

    @Test
    void commentValidation_blankOrTooLong() throws Exception {
        UserAccount ana = testData.createUser();
        String id = body(create(ana, null, "perfil")).get("id").asText();

        comment(ana, id, "   ").andExpect(status().isBadRequest());
        comment(ana, id, "x".repeat(1001)).andExpect(status().isBadRequest());
        comment(ana, id, "x".repeat(1000)).andExpect(status().isCreated());
        comment(ana, id, "ok <b>no html</b>").andExpect(status().isCreated());
        assertThat(counter(id, "comment_count")).isEqualTo(2);
    }

    @Test
    void whoCanComment_members_notTheMutedNorNonMembers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount muted = testData.createUser();
        UserAccount banned = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member, muted, banned)) {
            join(community, u);
        }
        makeModerator(owner, community, mod);
        String id = postIn(owner, community, "hola");
        moderate(owner, community, muted, "mute", null);
        moderate(owner, community, banned, "ban", "{\"reason\":\"Spam\"}");

        comment(owner, id, "uno").andExpect(status().isCreated());
        comment(mod, id, "dos").andExpect(status().isCreated());
        comment(member, id, "tres").andExpect(status().isCreated());
        comment(muted, id, "silenciada").andExpect(status().isForbidden());
        comment(outsider, id, "ajena").andExpect(status().isForbidden());
        comment(banned, id, "expulsada").andExpect(status().isNotFound());
        assertThat(counter(id, "comment_count")).isEqualTo(3);
        // Everybody who can see the post can read the comments
        assertThat(body(comments(outsider, id, null, 20)).get("items")).hasSize(3);
        assertThat(body(comments(muted, id, null, 20)).get("items")).hasSize(3);
        comments(banned, id, null, 20).andExpect(status().isNotFound());
    }

    @Test
    void commentsOnAProfilePost_areForWhoeverCanSeeIt_exceptTheBlocked() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount carla = testData.createUser();
        String id = body(create(ana, null, "perfil")).get("id").asText();

        comment(bea, id, "hola").andExpect(status().isCreated());
        comment(carla, id, "hola").andExpect(status().isCreated());
        // Bea blocks Carla: Bea no longer sees Carla's comment (nor Carla Bea's); Ana sees both
        mockMvc.perform(post("/api/users/" + carla.getUsername() + "/block").with(auth.bearer(bea))).andExpect(status().is2xxSuccessful());
        assertThat(authors(body(comments(bea, id, null, 20)))).containsExactly(bea.getUsername());
        assertThat(authors(body(comments(carla, id, null, 20)))).containsExactly(carla.getUsername());
        assertThat(authors(body(comments(ana, id, null, 20)))).containsExactly(bea.getUsername(), carla.getUsername());

        // Blocking the author of the post hides the post itself
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(carla))).andExpect(status().is2xxSuccessful());
        comments(carla, id, null, 20).andExpect(status().isNotFound());
        comment(carla, id, "otra").andExpect(status().isNotFound());
        comment(ana, id, "respuesta").andExpect(status().isCreated());
    }

    @Test
    void theStaff_seesAndDeletesEveryCommentOfTheirCommunity_eventThroughBlocksInBothDirections() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, ana, bea)) {
            join(community, u);
        }
        makeModerator(owner, community, mod);
        String id = postIn(owner, community, "hola");
        String byAna = body(comment(ana, id, "de ana")).get("id").asText();
        String byBea = body(comment(bea, id, "de bea")).get("id").asText();

        // The moderator blocked Ana, and Bea blocked the moderator: the plain rule would hide both comments
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(mod))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/users/" + mod.getUsername() + "/block").with(auth.bearer(bea))).andExpect(status().is2xxSuccessful());

        JsonNode seenByMod = body(comments(mod, id, null, 20));
        assertThat(ids(seenByMod)).containsExactly(byAna, byBea);
        assertThat(seenByMod.get("items").get(0).get("canDelete").asBoolean()).isTrue();
        assertThat(ids(body(comments(owner, id, null, 20)))).containsExactly(byAna, byBea);
        // A plain member does not get what the moderators get: Ana blocked nobody but Bea blocks the moderator only
        assertThat(ids(body(comments(ana, id, null, 20)))).containsExactly(byAna, byBea);
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(bea))).andExpect(status().is2xxSuccessful());
        assertThat(ids(body(comments(bea, id, null, 20)))).containsExactly(byBea);
        assertThat(body(comments(bea, id, null, 20)).get("items").get(0).get("canDelete").asBoolean()).isTrue();

        // The moderator deletes both even so (D-026); deleting twice is a 404; the counter goes down once each
        assertThat(counter(id, "comment_count")).isEqualTo(2);
        deleteComment(mod, id, byAna).andExpect(status().isNoContent());
        deleteComment(mod, id, byAna).andExpect(status().isNotFound());
        deleteComment(mod, id, byBea).andExpect(status().isNoContent());
        assertThat(counter(id, "comment_count")).isZero();
        assertThat(ids(body(comments(owner, id, null, 20)))).isEmpty();
    }

    @Test
    void whoCanDeleteAComment_itsAuthorAndTheStaff_notOthers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(community, ana);
        join(community, bea);
        String id = postIn(owner, community, "hola");
        String byAna = body(comment(ana, id, "de ana")).get("id").asText();

        assertThat(body(comments(bea, id, null, 20)).get("items").get(0).get("canDelete").asBoolean()).isFalse();
        deleteComment(bea, id, byAna).andExpect(status().isForbidden());
        deleteComment(ana, id, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        deleteComment(ana, id, byAna).andExpect(status().isNoContent());
        assertThat(counter(id, "comment_count")).isZero();

        String second = body(comment(ana, id, "otra")).get("id").asText();
        deleteComment(owner, id, second).andExpect(status().isNoContent());
        // A comment of another post is not found through this one
        String other = postIn(owner, community, "otra publicación");
        String third = body(comment(ana, other, "x")).get("id").asText();
        deleteComment(owner, id, third).andExpect(status().isNotFound());
    }

    @Test
    void deletingAPost_hidesItsCommentsAndLikes() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String id = body(create(ana, null, "perfil")).get("id").asText();
        comment(bea, id, "hola").andExpect(status().isCreated());
        like(bea, id).andExpect(status().isOk());

        mockMvc.perform(delete("/api/posts/" + id).with(auth.bearer(ana))).andExpect(status().isNoContent());
        comments(bea, id, null, 20).andExpect(status().isNotFound());
        comment(bea, id, "tarde").andExpect(status().isNotFound());
        like(bea, id).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- notification

    @Test
    void aComment_notifiesTheAuthorOfThePost_oncePerPersonWhileUnread_neverHerself() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount carla = testData.createUser();
        String id = body(create(ana, null, "perfil")).get("id").asText();

        comment(ana, id, "me respondo a mí misma").andExpect(status().isCreated());
        comment(bea, id, "uno").andExpect(status().isCreated());
        comment(bea, id, "dos").andExpect(status().isCreated());
        comment(carla, id, "tres").andExpect(status().isCreated());

        List<JsonNode> items = awaitNotifications(ana, 2);
        assertThat(items).extracting(n -> n.get("type").asText()).containsOnly("POST_COMMENT");
        assertThat(items).extracting(n -> n.get("actor").get("username").asText())
                .containsExactlyInAnyOrder(bea.getUsername(), carla.getUsername());
        assertThat(items.get(0).get("target").get("type").asText()).isEqualTo("POST");
        assertThat(items.get(0).get("target").get("ref").asText()).isEqualTo(id);
        // Give a late third one the chance to show up before asserting there is none
        Thread.sleep(300);
        assertThat(notifications(ana)).hasSize(2);
        assertThat(notifications(bea)).isEmpty();
    }

    @Test
    void aBlockedUser_cannotCommentSoNothingIsNotified() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String id = body(create(ana, null, "perfil")).get("id").asText();
        UserAccount carla = testData.createUser();
        comment(carla, id, "dime").andExpect(status().isCreated());
        awaitNotifications(ana, 1);
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(bea))).andExpect(status().is2xxSuccessful());
        comment(bea, id, "no debería").andExpect(status().isNotFound());
        Thread.sleep(300);
        assertThat(notifications(ana)).hasSize(1);
    }

    // ---------------------------------------------------------------- explore

    @Test
    void explore_showsPhotoPostsOfPublicCommunitiesAndProfiles_notWhatIsInHome_norPrivateOrBlockedOrBanned() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount stranger = testData.createUser();
        UserAccount followed = testData.createUser();
        UserAccount blocked = testData.createUser();
        UserAccount owner = testData.createUser();
        Community open = testData.createCommunity(owner);
        Community mine = testData.createCommunity(owner);
        Community closed = testData.createCommunity(owner);
        Community bannedFrom = testData.createCommunity(owner);
        join(mine, me);
        join(bannedFrom, me);
        mockMvc.perform(patchVisibility(owner, closed, "PRIVATE")).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/follow/" + followed.getUsername()).with(auth.bearer(me))).andExpect(status().is2xxSuccessful());

        String inOpen = postWithPhoto(owner, open.getSlug());
        String strangers = postWithPhoto(stranger, null);
        String noPhoto = body(create(stranger, null, "sin foto")).get("id").asText();
        String inMine = postWithPhoto(owner, mine.getSlug());
        String followeds = postWithPhoto(followed, null);
        String mineOwn = postWithPhoto(me, null);
        String inClosed = postWithPhoto(owner, closed.getSlug());
        String blockeds = postWithPhoto(blocked, null);
        String inBanned = postWithPhoto(owner, bannedFrom.getSlug());
        moderate(owner, bannedFrom, me, "ban", "{\"reason\":\"Spam\"}");
        mockMvc.perform(post("/api/users/" + blocked.getUsername() + "/block").with(auth.bearer(me))).andExpect(status().is2xxSuccessful());

        List<String> explored = ids(body(explore(me, null, 50)));
        assertThat(explored).contains(inOpen, strangers);
        assertThat(explored).doesNotContain(noPhoto, inMine, followeds, mineOwn, inClosed, blockeds, inBanned);
        // Newest first: the stranger's profile post is newer than the one in the open community
        assertThat(explored.indexOf(strangers)).isLessThan(explored.indexOf(inOpen));
        assertThat(body(explore(me, null, 50)).toString()).doesNotContain("\"postId\"", "\"authorId\"", "\"communityId\"");
        // A member of the closed community, though, does not get it in Explore either: Explore is public things only
        assertThat(ids(body(explore(owner, null, 50)))).doesNotContain(inClosed);
    }

    @Test
    void explore_pagesWithAnOpaqueCursor_andTheStatementCountDoesNotGrowWithThePage() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount author = testData.createUser();
        postWithPhoto(author, null);
        long small = exploreStatements(me);
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            all.add(0, postWithPhoto(author, null));
        }
        long big = exploreStatements(me);
        assertThat(big).isEqualTo(small);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = body(explore(me, cursor, 5));
            seen.addAll(ids(page));
            cursor = page.get("nextBefore").isNull() ? null : page.get("nextBefore").asText();
        } while (cursor != null);
        assertThat(seen).containsAll(all).doesNotHaveDuplicates();
        explore(me, "bm90LWEtY3Vyc29y", 5).andExpect(status().isBadRequest());
    }

    @Test
    void commentsAndLikes_runAConstantNumberOfStatements_whateverThePageSize() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(community, bea);
        String id = postIn(owner, community, "hola");
        comment(bea, id, "uno").andExpect(status().isCreated());
        // The POST_COMMENT notifications are made on another thread and would add statements to the count
        awaitNotifications(owner, 1);
        Thread.sleep(500);
        long small = commentStatements(owner, id);
        for (int i = 0; i < 15; i++) {
            comment(i % 2 == 0 ? bea : owner, id, "otro " + i).andExpect(status().isCreated());
        }
        Thread.sleep(500);
        long big = commentStatements(owner, id);
        assertThat(body(comments(owner, id, null, 50)).get("items")).hasSize(16);
        assertThat(big).isEqualTo(small);
    }

    // ---------------------------------------------------------------- helpers

    private long exploreStatements(UserAccount viewer) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        explore(viewer, null, 50).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private long commentStatements(UserAccount viewer, String postId) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        comments(viewer, postId, null, 50).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private List<JsonNode> awaitNotifications(UserAccount user, int count) throws Exception {
        await().atMost(Duration.ofSeconds(10)).until(() -> notifications(user).size() >= count);
        return notifications(user);
    }

    private List<JsonNode> notifications(UserAccount user) throws Exception {
        JsonNode page = body(mockMvc.perform(get("/api/notifications").with(auth.bearer(user))).andExpect(status().isOk()));
        List<JsonNode> items = new ArrayList<>();
        page.get("items").forEach(items::add);
        return items;
    }

    private String postWithPhoto(UserAccount user, String communitySlug) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("communitySlug", communitySlug);
        request.put("body", "con foto");
        request.put("mediaIds", List.of(upload(user)));
        return body(mockMvc.perform(post("/api/posts").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(request))).andExpect(status().isCreated())).get("id").asText();
    }

    private String postIn(UserAccount user, Community community, String text) throws Exception {
        return body(create(user, community.getSlug(), text).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions create(UserAccount user, String communitySlug, String text) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("communitySlug", communitySlug);
        request.put("body", text);
        return mockMvc.perform(post("/api/posts").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private ResultActions getPost(UserAccount user, String id) throws Exception {
        return mockMvc.perform(get("/api/posts/" + id).with(auth.bearer(user)));
    }

    private ResultActions like(UserAccount user, String id) throws Exception {
        return mockMvc.perform(put("/api/posts/" + id + "/like").with(auth.bearer(user)));
    }

    private ResultActions unlike(UserAccount user, String id) throws Exception {
        return mockMvc.perform(delete("/api/posts/" + id + "/like").with(auth.bearer(user)));
    }

    private ResultActions comment(UserAccount user, String id, String text) throws Exception {
        return mockMvc.perform(post("/api/posts/" + id + "/comments").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", text))));
    }

    private ResultActions comments(UserAccount user, String id, String cursor, int limit) throws Exception {
        var request = get("/api/posts/" + id + "/comments").param("limit", String.valueOf(limit)).with(auth.bearer(user));
        if (cursor != null) {
            request = request.param("after", cursor);
        }
        return mockMvc.perform(request);
    }

    private ResultActions deleteComment(UserAccount user, String postId, String commentId) throws Exception {
        return mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + commentId).with(auth.bearer(user)));
    }

    private ResultActions explore(UserAccount user, String cursor, int limit) throws Exception {
        var request = get("/api/explore/posts").param("limit", String.valueOf(limit)).with(auth.bearer(user));
        if (cursor != null) {
            request = request.param("before", cursor);
        }
        return mockMvc.perform(request);
    }

    private void join(Community community, UserAccount user) throws Exception {
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/join").with(auth.bearer(user))).andExpect(status().isCreated());
    }

    private void moderate(UserAccount actor, Community community, UserAccount target, String verb, String payload) throws Exception {
        var request = post("/api/communities/" + community.getPublicId() + "/members/" + target.getUsername() + "/" + verb).with(auth.bearer(actor));
        if (payload != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        mockMvc.perform(request).andExpect(status().isNoContent());
    }

    private void makeModerator(UserAccount owner, Community community, UserAccount target) throws Exception {
        mockMvc.perform(put("/api/communities/" + community.getPublicId() + "/members/" + target.getUsername() + "/role")
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchVisibility(UserAccount owner, Community community, String visibility) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/communities/" + community.getPublicId())
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + visibility + "\"}");
    }

    private long counter(String publicId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM posts WHERE public_id = ?::uuid", Long.class, publicId);
    }

    private long likesRows(String publicId) {
        return jdbc.queryForObject("SELECT count(*) FROM post_likes l JOIN posts p ON p.id = l.post_id WHERE p.public_id = ?::uuid",
                Long.class, publicId);
    }

    private UUID upload(UserAccount user) throws Exception {
        String response = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(200, 150)))
                        .param("purpose", "POST_IMAGE")
                        .with(auth.bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private static List<String> authors(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("items").forEach(item -> names.add(item.get("author").get("username").asText()));
        return names;
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
