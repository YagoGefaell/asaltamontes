package io.github.yagogefaell.asaltamontes.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.maintenance.CommunityMemberCounterMaintenanceService;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.community.service.CommunityModerationService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T19a: roles, mute, expel and readmit, "Expulsadas", ownership, private communities and the moderation log. */
class CommunityModerationIT extends AbstractIntegrationTest {

    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityModerationService moderationService;
    @Autowired private CommunityMemberCounterMaintenanceService recountService;
    @Autowired private JdbcTemplate jdbc;

    // ---- expel / readmit ----

    @Test
    void ban_removesHerFromTheListAndTheCount_inTheSameTransaction_andRecordsItInTheLog() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(member, community);
        join(other, community);
        assertThat(count(community)).isEqualTo(3);

        action(owner, community, member, "ban", "{\"reason\":\"Spam\"}").andExpect(status().isNoContent());

        // The counter moved with the ban itself, and it is what the nightly recount would say too
        assertThat(count(community)).isEqualTo(2);
        recountService.recountCountersInBatches();
        assertThat(count(community)).isEqualTo(2);

        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.content[*].username").value(not(hasItem(member.getUsername()))));

        JsonNode banned = body(mockMvc.perform(get(url(community, "/banned")).with(auth.bearer(owner))).andExpect(status().isOk()));
        assertThat(banned.get("items")).hasSize(1);
        JsonNode first = banned.get("items").get(0);
        assertThat(first.get("username").asText()).isEqualTo(member.getUsername());
        assertThat(first.get("reason").asText()).isEqualTo("Spam");
        assertThat(first.get("bannedBy").asText()).isEqualTo(owner.getUsername());
        assertThat(first.get("bannedAt").asText()).isNotBlank();
        assertThat(first.has("userId")).isFalse();

        JsonNode log = body(mockMvc.perform(get(url(community, "/moderation-log")).with(auth.bearer(owner))).andExpect(status().isOk()));
        assertThat(log.get("items").get(0).get("action").asText()).isEqualTo("BAN");
        assertThat(log.get("items").get(0).get("targetUsername").asText()).isEqualTo(member.getUsername());
    }

    @Test
    void banTwice_countsOnce_evenWhenTwoModeratorsActAtTheSameTime() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod1 = testData.createUser();
        UserAccount mod2 = testData.createUser();
        UserAccount target = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod1, mod2, target)) {
            join(u, community);
        }
        for (UserAccount mod : List.of(mod1, mod2)) {
            mockMvc.perform(put(url(community, "/members/" + mod.getUsername() + "/role")).with(auth.bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}")).andExpect(status().isNoContent());
        }
        assertThat(count(community)).isEqualTo(4);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (UserAccount mod : List.of(mod1, mod2)) {
                Callable<Object> ban = () -> {
                    moderationService.ban(community.getPublicId(), target.getUsername(), "x", mod);
                    return null;
                };
                results.add(pool.submit(ban));
            }
            for (Future<Object> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(count(community)).isEqualTo(3);
        assertThat(memberRepository.findStatus(community.getId(), target.getId())).contains(MemberStatus.BANNED);
    }

    @Test
    void unban_bringsHerBackToTheListAndTheCount_andIsLogged() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(member, community);
        action(owner, community, member, "ban", "{\"reason\":\"Spam\"}").andExpect(status().isNoContent());
        assertThat(count(community)).isEqualTo(1);

        action(owner, community, member, "unban", null).andExpect(status().isNoContent());

        assertThat(count(community)).isEqualTo(2);
        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.content[*].username").value(hasItem(member.getUsername())));
        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(member))).andExpect(status().isOk());
        mockMvc.perform(get(url(community, "/banned")).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.items.length()").value(0));
        JsonNode log = body(mockMvc.perform(get(url(community, "/moderation-log")).with(auth.bearer(owner))));
        assertThat(log.get("items").get(0).get("action").asText()).isEqualTo("UNBAN");
        assertThat(log.get("items").get(1).get("action").asText()).isEqualTo("BAN");

        // Readmitting somebody who is not expelled finds nothing
        action(owner, community, member, "unban", null).andExpect(status().isNotFound());
        assertThat(count(community)).isEqualTo(2);
    }

    @Test
    void theOwnerCannotBeExpelledOrMuted_nobodyActsOnHerself_andAModeratorOnlyOnMembers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount otherMod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, otherMod, member)) {
            join(u, community);
        }
        promote(owner, community, mod);
        promote(owner, community, otherMod);

        action(mod, community, owner, "ban", null).andExpect(status().isConflict());
        action(mod, community, owner, "mute", null).andExpect(status().isConflict());
        action(owner, community, owner, "ban", null).andExpect(status().isConflict());
        action(mod, community, mod, "ban", null).andExpect(status().isBadRequest());
        action(mod, community, otherMod, "ban", null).andExpect(status().isForbidden());
        action(mod, community, otherMod, "mute", null).andExpect(status().isForbidden());
        assertThat(count(community)).isEqualTo(4);

        action(mod, community, member, "ban", null).andExpect(status().isNoContent());
        // The owner can expel a moderator; she goes back to a plain member's role
        action(owner, community, otherMod, "ban", "{\"reason\":\"Abuso\"}").andExpect(status().isNoContent());
        assertThat(memberRepository.findByCommunityIdAndUserAccountId(community.getId(), otherMod.getId()).orElseThrow().getRole())
                .isEqualTo(CommunityRole.MEMBER);
        assertThat(count(community)).isEqualTo(2);
    }

    // ---- who can see the moderation ----

    @Test
    void bannedList_isOnlyForTheModeration_everybodyElseGetsTheSame404_includingABannedModerator() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        UserAccount bannedMod = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member, bannedMod)) {
            join(u, community);
        }
        promote(owner, community, mod);
        promote(owner, community, bannedMod);
        action(owner, community, bannedMod, "ban", null).andExpect(status().isNoContent());

        for (String path : List.of("/banned", "/moderation-log", "/requests")) {
            mockMvc.perform(get(url(community, path)).with(auth.bearer(owner))).andExpect(status().isOk());
            mockMvc.perform(get(url(community, path)).with(auth.bearer(mod))).andExpect(status().isOk());
            for (UserAccount denied : List.of(member, outsider, bannedMod)) {
                mockMvc.perform(get(url(community, path)).with(auth.bearer(denied)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.detail").value("Comunidad no encontrada"));
            }
        }
        mockMvc.perform(get("/api/communities/" + UUID.randomUUID() + "/banned").with(auth.bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comunidad no encontrada"));

        // A banned moderator sees the community as an outsider: 404 on the members, no role in the detail
        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(bannedMod))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(bannedMod)))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.joinRequested").value(false));
        // ...and cannot act any more
        action(bannedMod, community, member, "ban", null).andExpect(status().isNotFound());
        action(bannedMod, community, member, "mute", null).andExpect(status().isNotFound());
    }

    @Test
    void aModerator_seesTheStatusOfMutedMembers_andAPlainMemberDoesNot() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount muted = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : List.of(mod, member, muted)) {
            join(u, community);
        }
        promote(owner, community, mod);
        action(mod, community, muted, "mute", "{\"reason\":\"Insultos\"}").andExpect(status().isNoContent());

        // Still listed and still counted
        assertThat(count(community)).isEqualTo(4);
        JsonNode forMod = body(mockMvc.perform(get(url(community, "/members")).with(auth.bearer(mod))).andExpect(status().isOk()));
        JsonNode mutedForMod = find(forMod.get("content"), muted);
        assertThat(mutedForMod.get("status").asText()).isEqualTo("MUTED");
        JsonNode forMember = body(mockMvc.perform(get(url(community, "/members")).with(auth.bearer(member))).andExpect(status().isOk()));
        assertThat(find(forMember.get("content"), muted).has("status")).isFalse();

        action(mod, community, muted, "unmute", null).andExpect(status().isNoContent());
        JsonNode after = body(mockMvc.perform(get(url(community, "/members")).with(auth.bearer(mod))));
        assertThat(find(after.get("content"), muted).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void roles_areChangedByTheOwnerOnly_andOwnershipCanBeTransferred() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(mod, community);
        join(member, community);
        promote(owner, community, mod);

        String promote = "{\"role\":\"MODERATOR\"}";
        mockMvc.perform(put(url(community, "/members/" + member.getUsername() + "/role")).with(auth.bearer(mod))
                .contentType(MediaType.APPLICATION_JSON).content(promote)).andExpect(status().isForbidden());
        mockMvc.perform(put(url(community, "/members/" + mod.getUsername() + "/role")).with(auth.bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content(promote)).andExpect(status().isNotFound());
        mockMvc.perform(put(url(community, "/members/" + member.getUsername() + "/role")).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(url(community, "/members/" + owner.getUsername() + "/role")).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}")).andExpect(status().isConflict());

        // Moderators cannot hand over the ownership; the owner can
        mockMvc.perform(post(url(community, "/transfer-ownership/" + member.getUsername())).with(auth.bearer(mod)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url(community, "/transfer-ownership/" + mod.getUsername())).with(auth.bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(mod)))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.role").value("MODERATOR"));
        // The new owner cannot leave; the old one now can
        mockMvc.perform(post(url(community, "/leave")).with(auth.bearer(mod))).andExpect(status().isConflict());
        mockMvc.perform(post(url(community, "/leave")).with(auth.bearer(owner))).andExpect(status().isNoContent());
    }

    // ---- banned members and joining ----

    @Test
    void aBannedMember_cannotJoinNorRequest_andGetsANeutralMessage_inPublicAndPrivateCommunities() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(member, community);
        action(owner, community, member, "ban", null).andExpect(status().isNoContent());

        join(member, community).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No puedes unirte a esta comunidad"));
        patchCommunity(owner, community, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isOk());
        join(member, community).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No puedes unirte a esta comunidad"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_join_requests WHERE user_id = ?", Integer.class, member.getId()))
                .isZero();
        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(member)))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.joinRequested").value(false))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    // ---- private communities ----

    @Test
    void privateCommunity_joinCreatesARequest_theStaffAcceptsOrRejects_andTheRequesterIsNotified() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(mod, community);
        join(member, community);
        promote(owner, community, mod);
        patchCommunity(owner, community, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));

        join(ana, community).andExpect(status().isAccepted());
        join(ana, community).andExpect(status().isAccepted()); // asking twice leaves one request
        join(bea, community).andExpect(status().isAccepted());
        assertThat(count(community)).isEqualTo(3);
        assertThat(memberRepository.findStatus(community.getId(), ana.getId())).isEmpty();
        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.joinRequested").value(true))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.name").value(community.getName()));
        // She is not a member: no member list
        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(ana))).andExpect(status().isNotFound());

        // The owner and the moderator are told, once per requester
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notifications(owner, "COMMUNITY_JOIN_REQUEST")).isEqualTo(2);
            assertThat(notifications(mod, "COMMUNITY_JOIN_REQUEST")).isEqualTo(2);
        });
        assertThat(notifications(member, "COMMUNITY_JOIN_REQUEST")).isZero();

        JsonNode requests = body(mockMvc.perform(get(url(community, "/requests")).with(auth.bearer(mod))).andExpect(status().isOk()));
        assertThat(requests.get("items")).hasSize(2);

        mockMvc.perform(post(url(community, "/requests/" + ana.getUsername() + "/approve")).with(auth.bearer(mod)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(url(community, "/requests/" + bea.getUsername() + "/reject")).with(auth.bearer(owner)))
                .andExpect(status().isNoContent());
        // Decided once only
        mockMvc.perform(post(url(community, "/requests/" + ana.getUsername() + "/approve")).with(auth.bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(url(community, "/requests/" + ana.getUsername() + "/approve")).with(auth.bearer(member)))
                .andExpect(status().isNotFound());

        assertThat(memberRepository.findStatus(community.getId(), ana.getId())).contains(MemberStatus.ACTIVE);
        assertThat(memberRepository.findStatus(community.getId(), bea.getId())).isEmpty();
        assertThat(count(community)).isEqualTo(4);
        recountService.recountCountersInBatches();
        assertThat(count(community)).isEqualTo(4);
        mockMvc.perform(get(url(community, "/members")).with(auth.bearer(ana))).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notifications(ana, "COMMUNITY_JOIN_APPROVED")).isEqualTo(1);
            assertThat(notifications(bea, "COMMUNITY_JOIN_REJECTED")).isEqualTo(1);
        });
        // She can ask again after a rejection
        join(bea, community).andExpect(status().isAccepted());
    }

    @Test
    void leavingWithdrawsAPendingRequest() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        Community community = testData.createCommunity(owner);
        patchCommunity(owner, community, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isOk());
        join(ana, community).andExpect(status().isAccepted());

        mockMvc.perform(post(url(community, "/leave")).with(auth.bearer(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get(url(community, "/requests")).with(auth.bearer(owner))).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(post(url(community, "/leave")).with(auth.bearer(ana))).andExpect(status().isNotFound());
    }

    // ---- editing ----

    @Test
    void patch_followsD020_theModerationEdits_onlyTheOwnerChangesTheVisibility_andOthersGet404() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(mod, community);
        join(member, community);
        promote(owner, community, mod);
        String newName = testData.uniqueName("Renamed");

        patchCommunity(mod, community, "{\"name\":\"" + newName + "\",\"city\":\"Vigo\",\"province\":\"Pontevedra\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.slug").value(community.getSlug()))
                .andExpect(jsonPath("$.city").value("Vigo"));
        // Absent = unchanged; null clears
        patchCommunity(mod, community, "{\"description\":\"Nueva\"}").andExpect(jsonPath("$.city").value("Vigo"))
                .andExpect(jsonPath("$.description").value("Nueva"));
        patchCommunity(mod, community, "{\"city\":null,\"description\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.province").value("Pontevedra"));
        // The name cannot be removed, nor taken
        patchCommunity(mod, community, "{\"name\":null}").andExpect(status().isBadRequest());
        patchCommunity(mod, community, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        Community other = testData.createCommunity(owner);
        patchCommunity(mod, community, "{\"name\":\"" + other.getName() + "\"}").andExpect(status().isConflict());
        patchCommunity(mod, community, "{\"visibility\":\"SECRET\"}").andExpect(status().isBadRequest());

        patchCommunity(mod, community, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isForbidden());
        patchCommunity(owner, community, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isOk());
        patchCommunity(member, community, "{\"description\":\"x\"}").andExpect(status().isNotFound());
        // By slug or by public id
        mockMvc.perform(patch("/api/communities/" + community.getSlug()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"por slug\"}"))
                .andExpect(jsonPath("$.description").value("por slug"));
        mockMvc.perform(patch("/api/communities/missing-slug").with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());

        JsonNode log = body(mockMvc.perform(get(url(community, "/moderation-log")).with(auth.bearer(owner))));
        assertThat(log.get("items").get(0).get("action").asText()).isEqualTo("COMMUNITY_UPDATED");
    }

    @Test
    void search_filtersByCity_andProvince_andShowsTheVisibility() throws Exception {
        UserAccount owner = testData.createUser();
        Community vigo = testData.createCommunity(owner);
        Community lugo = testData.createCommunity(owner);
        patchCommunity(owner, vigo, "{\"city\":\"Vigo\",\"province\":\"Pontevedra\",\"visibility\":\"PRIVATE\"}").andExpect(status().isOk());
        patchCommunity(owner, lugo, "{\"city\":\"Lugo\",\"province\":\"Lugo\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/communities").param("city", "vigo").param("size", "50").with(auth.bearer(owner)))
                .andExpect(jsonPath("$.content[*].name").value(hasItem(vigo.getName())))
                .andExpect(jsonPath("$.content[*].name").value(not(hasItem(lugo.getName()))))
                .andExpect(jsonPath("$.content[0].visibility").value("PRIVATE"));
        mockMvc.perform(get("/api/communities").param("province", "LUGO").param("size", "50").with(auth.bearer(owner)))
                .andExpect(jsonPath("$.content[*].name").value(hasItem(lugo.getName())))
                .andExpect(jsonPath("$.content[*].name").value(not(hasItem(vigo.getName()))));
    }

    // ---- cursors ----

    @Test
    void moderationLog_pagesWithOpaqueCursors_capsTheSize_andRejectsForeignOrBrokenCursors() throws Exception {
        UserAccount owner = testData.createUser();
        Community community = testData.createCommunity(owner);
        Community other = testData.createCommunity(owner);
        List<UserAccount> members = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UserAccount u = testData.createUser();
            members.add(u);
            join(u, community);
            join(u, other);
            action(owner, community, u, "mute", null).andExpect(status().isNoContent());
        }
        action(owner, other, members.get(0), "mute", null).andExpect(status().isNoContent());
        action(owner, other, members.get(1), "mute", null).andExpect(status().isNoContent());

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            var request = get(url(community, "/moderation-log")).param("limit", "2").with(auth.bearer(owner));
            if (cursor != null) {
                request = request.param("before", cursor);
            }
            JsonNode page = body(mockMvc.perform(request).andExpect(status().isOk()));
            page.get("items").forEach(i -> seen.add(i.get("id").asText()));
            cursor = page.get("nextBefore").isNull() ? null : page.get("nextBefore").asText();
        } while (cursor != null);
        assertThat(seen).hasSize(5).doesNotHaveDuplicates();

        JsonNode big = body(mockMvc.perform(get(url(community, "/moderation-log")).param("limit", "2000").with(auth.bearer(owner))));
        assertThat(big.get("items").size()).isLessThanOrEqualTo(50);

        String foreign = body(mockMvc.perform(get(url(other, "/moderation-log")).param("limit", "1").with(auth.bearer(owner))))
                .get("nextBefore").asText();
        mockMvc.perform(get(url(community, "/moderation-log")).param("before", foreign).with(auth.bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url(community, "/moderation-log")).param("before", "garbage").with(auth.bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url(community, "/banned")).param("before", foreign).with(auth.bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private String url(Community community, String path) {
        return "/api/communities/" + community.getPublicId() + path;
    }

    private long count(Community community) {
        return communityRepository.findById(community.getId()).orElseThrow().getMemberCount();
    }

    private int notifications(UserAccount user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ?", Integer.class, user.getId(), type);
    }

    private ResultActions join(UserAccount user, Community community) throws Exception {
        return mockMvc.perform(post(url(community, "/join")).with(auth.bearer(user)));
    }

    private void promote(UserAccount owner, Community community, UserAccount user) throws Exception {
        mockMvc.perform(put(url(community, "/members/" + user.getUsername() + "/role")).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}")).andExpect(status().isNoContent());
    }

    private ResultActions action(UserAccount actor, Community community, UserAccount target, String verb, String body) throws Exception {
        var request = post(url(community, "/members/" + target.getUsername() + "/" + verb)).with(auth.bearer(actor));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions patchCommunity(UserAccount actor, Community community, String body) throws Exception {
        return mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode members, UserAccount user) {
        for (JsonNode m : members) {
            if (m.get("username").asText().equals(user.getUsername())) {
                return m;
            }
        }
        throw new AssertionError("not listed: " + user.getUsername());
    }
}
