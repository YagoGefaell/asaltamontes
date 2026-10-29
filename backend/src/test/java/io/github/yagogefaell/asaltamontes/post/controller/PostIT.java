package io.github.yagogefaell.asaltamontes.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import jakarta.persistence.EntityManagerFactory;

/** T22a1: publications in profiles and communities, who sees them, the feed, the walls and deleting. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MediaCleanupService mediaCleanup;
    @Autowired private UserService userService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    // ---------------------------------------------------------------- create

    @Test
    void aPostOnTheProfileAndOneInACommunity_withPhotos_comeBackWithoutInternalIds() throws Exception {
        UserAccount ana = testData.createUser();
        Community community = testData.createCommunity(ana);
        UUID photo1 = upload(ana, "POST_IMAGE");
        UUID photo2 = upload(ana, "POST_IMAGE");

        JsonNode profilePost = body(create(ana, null, "Mi primera salida", List.of(photo1, photo2))
                .andExpect(status().isCreated()));
        assertThat(profilePost.get("community").isNull()).isTrue();
        assertThat(profilePost.get("media")).hasSize(2);
        assertThat(profilePost.get("media").get(0).get("thumb").asText()).contains("/media/post-image/" + photo1 + "/thumb.jpg");
        assertThat(profilePost.get("media").get(1).get("large").asText()).contains(photo2.toString());
        assertThat(profilePost.get("author").get("username").asText()).isEqualTo(ana.getUsername());
        assertThat(profilePost.get("canDelete").asBoolean()).isTrue();
        assertThat(profilePost.get("likedByMe").asBoolean()).isFalse();
        assertThat(profilePost.get("likeCount").asInt()).isZero();
        UUID.fromString(profilePost.get("id").asText());

        JsonNode communityPost = body(create(ana, community.getSlug(), "Hoy en el grupo", List.of())
                .andExpect(status().isCreated()));
        assertThat(communityPost.get("community").get("slug").asText()).isEqualTo(community.getSlug());
        assertThat(communityPost.get("media")).isEmpty();

        // The only ids in the answers are UUIDs (never the bigint key)
        assertThat(communityPost.toString()).doesNotContain("\"postId\"", "\"authorId\"", "\"communityId\"");
    }

    @Test
    void validations_blankTooLongTooManyOrRepeatedPhotos_andSomebodyElsesOrWrongPurposePhoto() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID mine = upload(ana, "POST_IMAGE");
        UUID avatar = upload(ana, "AVATAR");
        UUID beas = upload(bea, "POST_IMAGE");

        create(ana, null, "   ", List.of()).andExpect(status().isBadRequest());
        create(ana, null, "x".repeat(2001), List.of()).andExpect(status().isBadRequest());
        create(ana, null, "x".repeat(2000), List.of()).andExpect(status().isCreated());
        List<UUID> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(upload(ana, "POST_IMAGE"));
        }
        create(ana, null, "cinco", five).andExpect(status().isBadRequest());
        create(ana, null, "repetida", List.of(mine, mine)).andExpect(status().isBadRequest());
        create(ana, null, "ajena", List.of(beas)).andExpect(status().isNotFound());
        create(ana, null, "inexistente", List.of(UUID.randomUUID())).andExpect(status().isNotFound());
        create(ana, null, "otro uso", List.of(avatar)).andExpect(status().isBadRequest());
        create(ana, null, "cuatro", five.subList(0, 4)).andExpect(status().isCreated());
    }

    @Test
    void postingInACommunity_isOnlyForTheOwnerAndTheModerators() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount muted = testData.createUser();
        UserAccount banned = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member, muted, banned)) {
            mockMvc.perform(post(url(community, "/join")).with(auth.bearer(u))).andExpect(status().isCreated());
        }
        makeModerator(owner, community, mod);
        moderate(owner, community, muted, "mute", null);
        moderate(owner, community, banned, "ban", "{\"reason\":\"Spam\"}");

        create(owner, community.getSlug(), "hola", List.of()).andExpect(status().isCreated());
        create(mod, community.getSlug(), "hola", List.of()).andExpect(status().isCreated());
        create(member, community.getSlug(), "hola", List.of()).andExpect(status().isForbidden());
        create(muted, community.getSlug(), "hola", List.of()).andExpect(status().isForbidden());
        create(outsider, community.getSlug(), "hola", List.of()).andExpect(status().isForbidden());
        create(banned, community.getSlug(), "hola", List.of()).andExpect(status().isNotFound());
        create(member, "no-existe", "hola", List.of()).andExpect(status().isNotFound());
        // On her own profile anybody posts
        create(member, null, "en mi perfil", List.of()).andExpect(status().isCreated());
        create(banned, null, "en mi perfil", List.of()).andExpect(status().isCreated());

        // In a private community, who cannot see it gets the 404 of a missing one
        patchVisibility(owner, community, "PRIVATE");
        create(outsider, community.getSlug(), "hola", List.of()).andExpect(status().isNotFound());
        create(member, community.getSlug(), "hola", List.of()).andExpect(status().isForbidden());
        create(mod, community.getSlug(), "hola", List.of()).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- visibility

    @Test
    void aPublicCommunityPost_isSeenByAnyUser_aPrivateOneOnlyByItsMembers_neverByTheBanned() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        UserAccount banned = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(member, banned)) {
            mockMvc.perform(post(url(community, "/join")).with(auth.bearer(u))).andExpect(status().isCreated());
        }
        String id = body(create(owner, community.getSlug(), "Para todas", List.of())).get("id").asText();
        moderate(owner, community, banned, "ban", "{\"reason\":\"Spam\"}");

        // Public: members and outsiders read it (post and wall); only the feed is limited to her communities
        for (UserAccount who : List.of(member, outsider)) {
            getPost(who, id).andExpect(status().isOk());
            assertThat(ids(wall(who, community))).containsExactly(id);
        }
        assertThat(ids(feed(member, null))).containsExactly(id);
        assertThat(ids(feed(outsider, null))).isEmpty();
        getPost(banned, id).andExpect(status().isNotFound());
        wallRequest(banned, community).andExpect(status().isNotFound());
        assertThat(ids(feed(banned, null))).isEmpty();

        // Private: members only
        patchVisibility(owner, community, "PRIVATE");
        getPost(member, id).andExpect(status().isOk());
        assertThat(ids(wall(member, community))).containsExactly(id);
        getPost(outsider, id).andExpect(status().isNotFound());
        wallRequest(outsider, community).andExpect(status().isNotFound());
        getPost(banned, id).andExpect(status().isNotFound());
        wallRequest(banned, community).andExpect(status().isNotFound());
    }

    @Test
    void thePostsOfSomebodyWhoLeftTheCommunity_stayVisible() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member)) {
            mockMvc.perform(post(url(community, "/join")).with(auth.bearer(u))).andExpect(status().isCreated());
        }
        makeModerator(owner, community, mod);
        String id = body(create(mod, community.getSlug(), "antes de irme", List.of())).get("id").asText();
        mockMvc.perform(post(url(community, "/leave")).with(auth.bearer(mod))).andExpect(status().is2xxSuccessful());

        assertThat(ids(wall(member, community))).containsExactly(id);
        getPost(member, id).andExpect(status().isOk());
        // The owner still deletes it, and she can no longer post
        create(mod, community.getSlug(), "ya no", List.of()).andExpect(status().isForbidden());
        deletePost(owner, id).andExpect(status().isNoContent());
    }

    @Test
    void aProfilePost_isSeenByAnyUser_untilT23_butTheFeedOnlyShowsTheFollowed() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount follower = testData.createUser();
        UserAccount stranger = testData.createUser();
        follow(follower, ana);
        String id = body(create(ana, null, "Para quien quiera", List.of())).get("id").asText();

        for (UserAccount who : List.of(ana, follower, stranger)) {
            getPost(who, id).andExpect(status().isOk());
            assertThat(ids(profile(who, ana, null))).containsExactly(id);
        }
        assertThat(ids(feed(ana, null))).contains(id);
        assertThat(ids(feed(follower, null))).contains(id);
        assertThat(ids(feed(stranger, null))).doesNotContain(id);

        // Unfollowing only takes it out of the feed
        mockMvc.perform(delete("/api/users/follow/" + ana.getUsername()).with(auth.bearer(follower))).andExpect(status().isNoContent());
        getPost(follower, id).andExpect(status().isOk());
        assertThat(ids(feed(follower, null))).doesNotContain(id);

        mockMvc.perform(get("/api/users/no_existe_nadie/posts").with(auth.bearer(ana))).andExpect(status().isNotFound());
    }

    @Test
    void aBlock_inEitherDirection_hidesThePostsOfTheOtherOne() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(ana, bea)) {
            mockMvc.perform(post(url(community, "/join")).with(auth.bearer(u))).andExpect(status().isCreated());
        }
        makeModerator(owner, community, ana);
        makeModerator(owner, community, bea);
        follow(bea, ana);
        follow(ana, bea);
        String anasCommunityPost = body(create(ana, community.getSlug(), "de ana", List.of())).get("id").asText();
        String anasProfilePost = body(create(ana, null, "perfil de ana", List.of())).get("id").asText();
        String beasPost = body(create(bea, community.getSlug(), "de bea", List.of())).get("id").asText();
        assertThat(ids(feed(bea, null))).contains(anasCommunityPost, anasProfilePost);

        // Bea blocks Ana: neither sees the other's posts (block is symmetric)
        mockMvc.perform(post("/api/users/" + ana.getUsername() + "/block").with(auth.bearer(bea))).andExpect(status().is2xxSuccessful());

        for (String hidden : List.of(anasCommunityPost, anasProfilePost)) {
            getPost(bea, hidden).andExpect(status().isNotFound());
        }
        getPost(ana, beasPost).andExpect(status().isNotFound());
        assertThat(ids(feed(bea, null))).doesNotContain(anasCommunityPost, anasProfilePost);
        assertThat(ids(feed(ana, null))).doesNotContain(beasPost);
        assertThat(ids(wall(bea, community))).doesNotContain(anasCommunityPost);
        assertThat(ids(wall(ana, community))).doesNotContain(beasPost);
        wallRequestProfile(bea, ana).andExpect(status().isNotFound());
        wallRequestProfile(ana, bea).andExpect(status().isNotFound());
        // The others still see both
        assertThat(ids(wall(owner, community))).contains(anasCommunityPost, beasPost);
    }

    // ---------------------------------------------------------------- feed and pagination

    @Test
    void theFeed_mixesCommunitiesAndFollowed_newestFirst_andPagesWithoutDuplicatesOrGaps() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount friend = testData.createUser();
        UserAccount owner = testData.createUser();
        UserAccount stranger = testData.createUser();
        Community mine = testData.createCommunity(owner);
        Community notMine = testData.createCommunity(stranger);
        mockMvc.perform(post(url(mine, "/join")).with(auth.bearer(me))).andExpect(status().isCreated());
        follow(me, friend);

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            expected.add(body(create(owner, mine.getSlug(), "comunidad " + i, List.of())).get("id").asText());
            expected.add(body(create(friend, null, "amiga " + i, List.of())).get("id").asText());
            create(stranger, notMine.getSlug(), "ajena " + i, List.of()).andExpect(status().isCreated());
            create(stranger, null, "perfil ajeno " + i, List.of()).andExpect(status().isCreated());
        }
        expected.add(body(create(me, null, "mía", List.of())).get("id").asText());
        java.util.Collections.reverse(expected);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = feed(me, cursor, 3);
            page.get("items").forEach(item -> seen.add(item.get("id").asText()));
            cursor = page.get("nextBefore").isNull() ? null : page.get("nextBefore").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(new HashSet<>(seen)).hasSameSizeAs(seen);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void pagination_ofAWall_acceptsOnlyItsOwnKindOfCursor() throws Exception {
        UserAccount ana = testData.createUser();
        for (int i = 0; i < 3; i++) {
            create(ana, null, "post " + i, List.of()).andExpect(status().isCreated());
        }
        JsonNode first = profile(ana, ana, null, 2);
        assertThat(first.get("items")).hasSize(2);
        String cursor = first.get("nextBefore").asText();
        assertThat(profile(ana, ana, cursor, 2).get("items")).hasSize(1);
        assertThat(profile(ana, ana, cursor, 2).get("nextBefore").isNull()).isTrue();

        mockMvc.perform(get("/api/feed").param("before", "garbage").with(auth.bearer(ana))).andExpect(status().isBadRequest());
        // A cursor from another list (here: a message cursor) is not valid for posts
        String foreign = io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor.encode(
                io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor.MESSAGE, java.time.LocalDateTime.now(), UUID.randomUUID());
        mockMvc.perform(get("/api/feed").param("before", foreign).with(auth.bearer(ana))).andExpect(status().isBadRequest());
        // The limit is clamped
        assertThat(profile(ana, ana, null, 1000).get("items")).hasSize(3);
        assertThat(profile(ana, ana, null, 0).get("items")).hasSize(1);
    }

    @Test
    void theFeed_runsAConstantNumberOfStatements_whateverThePageSize() throws Exception {
        UserAccount me = testData.createUser();
        UserAccount friend = testData.createUser();
        UserAccount owner = testData.createUser();
        Community community = testData.createCommunity(owner);
        mockMvc.perform(post(url(community, "/join")).with(auth.bearer(me))).andExpect(status().isCreated());
        follow(me, friend);

        create(friend, null, "uno", List.of(upload(friend, "POST_IMAGE"))).andExpect(status().isCreated());
        long small = statementsFor(me);

        for (int i = 0; i < 12; i++) {
            create(friend, null, "perfil " + i, List.of(upload(friend, "POST_IMAGE"), upload(friend, "POST_IMAGE"))).andExpect(status().isCreated());
            create(owner, community.getSlug(), "comunidad " + i, List.of(upload(owner, "POST_IMAGE"))).andExpect(status().isCreated());
        }
        long big = statementsFor(me);

        assertThat(feed(me, null, 50).get("items")).hasSize(25);
        assertThat(big).isEqualTo(small);
    }

    // ---------------------------------------------------------------- delete

    @Test
    void whoCanDelete_theAuthor_theStaffOfTheCommunity_notOtherMembers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount other = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member, other)) {
            mockMvc.perform(post(url(community, "/join")).with(auth.bearer(u))).andExpect(status().isCreated());
        }
        makeModerator(owner, community, mod);

        String byMember = body(create(mod, community.getSlug(), "de la moderadora", List.of())).get("id").asText();
        JsonNode seenByOther = body(getPost(other, byMember).andExpect(status().isOk()));
        assertThat(seenByOther.get("canDelete").asBoolean()).isFalse();
        assertThat(body(getPost(mod, byMember)).get("canDelete").asBoolean()).isTrue();
        assertThat(body(getPost(owner, byMember)).get("canDelete").asBoolean()).isTrue();

        deletePost(other, byMember).andExpect(status().isForbidden());
        deletePost(outsider, byMember).andExpect(status().isForbidden());
        deletePost(owner, byMember).andExpect(status().isNoContent());
        deletePost(owner, byMember).andExpect(status().isNotFound());
        getPost(member, byMember).andExpect(status().isNotFound());
        assertThat(ids(wall(owner, community))).doesNotContain(byMember);
        assertThat(ids(feed(member, null))).doesNotContain(byMember);

        // The owner's post: a moderator may delete it too (staff), a plain member may not
        String byOwner = body(create(owner, community.getSlug(), "de la propietaria", List.of())).get("id").asText();
        deletePost(member, byOwner).andExpect(status().isForbidden());
        deletePost(mod, byOwner).andExpect(status().isNoContent());

        // A profile post: only her
        String profilePost = body(create(member, null, "perfil", List.of())).get("id").asText();
        follow(other, member);
        assertThat(body(getPost(other, profilePost)).get("canDelete").asBoolean()).isFalse();
        deletePost(other, profilePost).andExpect(status().isForbidden());
        deletePost(member, profilePost).andExpect(status().isNoContent());
    }

    @Test
    void deletingAPost_releasesItsPhotos_butKeepsTheOnesStillInUse() throws Exception {
        UserAccount ana = testData.createUser();
        UUID alone = upload(ana, "POST_IMAGE");
        UUID shared = upload(ana, "POST_IMAGE");
        String first = body(create(ana, null, "uno", List.of(alone, shared))).get("id").asText();
        String second = body(create(ana, null, "dos", List.of(shared))).get("id").asText();

        // In use: the orphan cleanup cannot take them
        assertThat(mediaCleanup.deleteIfUnreferenced(alone, null)).isFalse();
        assertThat(mediaCleanup.deleteIfUnreferenced(shared, null)).isFalse();

        deletePost(ana, first).andExpect(status().isNoContent());
        assertThat(mediaExists(alone)).isFalse();
        assertThat(mediaExists(shared)).isTrue();

        deletePost(ana, second).andExpect(status().isNoContent());
        assertThat(mediaExists(shared)).isFalse();
    }

    @Test
    void deletingTheAccount_removesItsPosts() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID photo = upload(ana, "POST_IMAGE");
        String id = body(create(ana, null, "adiós", List.of(photo))).get("id").asText();
        getPost(ana, id).andExpect(status().isOk());

        userService.deleteUser(ana.getId());

        getPost(bea, id).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE public_id = ?::uuid", Integer.class, id)).isZero();
        assertThat(mediaExists(photo)).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private long statementsFor(UserAccount viewer) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        feed(viewer, null, 50);
        return statistics.getPrepareStatementCount();
    }

    private ResultActions create(UserAccount user, String communitySlug, String text, List<UUID> media) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("communitySlug", communitySlug);
        request.put("body", text);
        request.put("mediaIds", media);
        return mockMvc.perform(post("/api/posts").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private ResultActions getPost(UserAccount user, String id) throws Exception {
        return mockMvc.perform(get("/api/posts/" + id).with(auth.bearer(user)));
    }

    private ResultActions deletePost(UserAccount user, String id) throws Exception {
        return mockMvc.perform(delete("/api/posts/" + id).with(auth.bearer(user)));
    }

    private JsonNode feed(UserAccount user, String cursor) throws Exception {
        return feed(user, cursor, 20);
    }

    private JsonNode feed(UserAccount user, String cursor, int limit) throws Exception {
        var request = get("/api/feed").param("limit", String.valueOf(limit)).with(auth.bearer(user));
        if (cursor != null) {
            request = request.param("before", cursor);
        }
        return body(mockMvc.perform(request).andExpect(status().isOk()));
    }

    private ResultActions wallRequest(UserAccount user, Community community) throws Exception {
        return mockMvc.perform(get("/api/communities/" + community.getSlug() + "/posts").with(auth.bearer(user)));
    }

    private JsonNode wall(UserAccount user, Community community) throws Exception {
        return body(wallRequest(user, community).andExpect(status().isOk()));
    }

    private ResultActions wallRequestProfile(UserAccount viewer, UserAccount author) throws Exception {
        return mockMvc.perform(get("/api/users/" + author.getUsername() + "/posts").with(auth.bearer(viewer)));
    }

    private JsonNode profile(UserAccount viewer, UserAccount author, String cursor) throws Exception {
        return profile(viewer, author, cursor, 20);
    }

    private JsonNode profile(UserAccount viewer, UserAccount author, String cursor, int limit) throws Exception {
        var request = get("/api/users/" + author.getUsername() + "/posts").param("limit", String.valueOf(limit)).with(auth.bearer(viewer));
        if (cursor != null) {
            request = request.param("before", cursor);
        }
        return body(mockMvc.perform(request).andExpect(status().isOk()));
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private void follow(UserAccount follower, UserAccount followed) throws Exception {
        mockMvc.perform(post("/api/users/follow/" + followed.getUsername()).with(auth.bearer(follower))).andExpect(status().is2xxSuccessful());
    }

    private void moderate(UserAccount actor, Community community, UserAccount target, String verb, String payload) throws Exception {
        var request = post(url(community, "/members/" + target.getUsername() + "/" + verb)).with(auth.bearer(actor));
        if (payload != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        mockMvc.perform(request).andExpect(status().isNoContent());
    }

    private void makeModerator(UserAccount owner, Community community, UserAccount target) throws Exception {
        mockMvc.perform(put(url(community, "/members/" + target.getUsername() + "/role")).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}")).andExpect(status().isNoContent());
    }

    private void patchVisibility(UserAccount owner, Community community, String visibility) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url(community, ""))
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"" + visibility + "\"}")).andExpect(status().isOk());
    }

    private String url(Community community, String path) {
        return "/api/communities/" + community.getPublicId() + path;
    }

    private UUID upload(UserAccount user, String purpose) throws Exception {
        String response = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(200, 150)))
                        .param("purpose", purpose)
                        .with(auth.bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private boolean mediaExists(UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM media WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
