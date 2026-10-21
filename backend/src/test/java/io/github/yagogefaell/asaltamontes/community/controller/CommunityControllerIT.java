package io.github.yagogefaell.asaltamontes.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.service.SlugGenerator;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class CommunityControllerIT extends AbstractIntegrationTest {

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createCommunity_thenItAppearsInMyCommunities() throws Exception {
        UserAccount owner = testData.createUser();
        String name = testData.uniqueName("Trail");

        create(owner, name).andExpect(status().isCreated());

        mockMvc.perform(get("/api/communities/me").with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(hasItem(name)))
                .andExpect(jsonPath("$.content[0].role").value("OWNER"));
    }

    @Test
    void createCommunity_withBlankName_returnsBadRequest() throws Exception {
        UserAccount owner = testData.createUser();

        create(owner, "").andExpect(status().isBadRequest());
    }

    @Test
    void aNameWithSymbols_getsASlug_andCanBeOpenedBySlugAndByItsOldNameUrl() throws Exception {
        UserAccount owner = testData.createUser();
        String suffix = testData.uniqueName("x");
        String name = "Trail Vigo / Sur #1 " + suffix;
        // Before T14 the URL carried the name; a name with "/" never worked there (BUG-013), one without it must
        String oldStyleName = "Trail Vigo Sur #2 " + suffix;
        String slug = SlugGenerator.slugify(name);

        create(owner, name)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/communities/" + slug))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(get("/api/communities/" + slug).with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        // Links shared before T14 (name in the URL, encoded)
        create(owner, oldStyleName).andExpect(status().isCreated());
        mockMvc.perform(get("/api/communities/{name}", oldStyleName).with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(SlugGenerator.slugify(oldStyleName)));
    }

    @Test
    void namesThatOnlyDifferInCaseOrSpaces_areTheSameCommunity() throws Exception {
        UserAccount owner = testData.createUser();
        String name = testData.uniqueName("Asaltamontes Female");

        create(owner, name).andExpect(status().isCreated());

        create(owner, "  " + name.toUpperCase().replace(" ", "   ") + " ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una comunidad con ese nombre"));
    }

    @Test
    void namesWithTheSameSlug_getANumberedSlug() throws Exception {
        UserAccount owner = testData.createUser();
        String base = testData.uniqueName("Montaña");
        String slug = SlugGenerator.slugify(base);

        create(owner, base).andExpect(jsonPath("$.slug").value(slug));
        create(owner, base + "!").andExpect(jsonPath("$.slug").value(slug + "-2"));
        create(owner, base + "?").andExpect(jsonPath("$.slug").value(slug + "-3"));
    }

    @Test
    void listings_workWithTheDefaultSort_andRejectUnknownSorts() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(member, community).andExpect(status().isCreated());

        mockMvc.perform(get("/api/communities/me").with(auth.bearer(member))).andExpect(status().isOk());
        mockMvc.perform(get("/api/communities/" + community.getId() + "/members").with(auth.bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(owner.getUsername()));
        mockMvc.perform(get("/api/communities").param("sort", "memberCount,desc").with(auth.bearer(member)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/communities/me").param("sort", "createdAt,desc").with(auth.bearer(member)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/communities/" + community.getId() + "/members").param("sort", "joinedAt,desc")
                        .with(auth.bearer(member)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/communities/me").param("sort", "role").with(auth.bearer(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parámetro de ordenación no válido"));
        mockMvc.perform(get("/api/communities/" + community.getId() + "/members").param("sort", "name")
                        .with(auth.bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joining_aMissingCommunityIs404_twiceIs409_andBannedIs403() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount banned = testData.createUser();
        Community community = testData.createCommunity(owner);

        mockMvc.perform(post("/api/communities/999999999/join").with(auth.bearer(member)))
                .andExpect(status().isNotFound());

        join(member, community).andExpect(status().isCreated());
        join(member, community)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya eres miembro de esta comunidad"));

        join(banned, community).andExpect(status().isCreated());
        transactionTemplate.executeWithoutResult(tx -> communityMemberRepository.findAll().stream()
                .filter(m -> m.getUserAccount().getId().equals(banned.getId()))
                .forEach(m -> m.setStatus(MemberStatus.BANNED)));
        join(banned, community).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/communities/" + community.getSlug()).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void leavingOrDeletingAMissingCommunity_is404() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(post("/api/communities/999999999/leave").with(auth.bearer(user)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/communities/999999999").with(auth.bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comunidad no encontrada"));
    }

    @Test
    void search_isCaseInsensitive_treatsWildcardsLiterally_andPagesThroughEverything() throws Exception {
        UserAccount owner = testData.createUser();
        String prefix = testData.uniqueName("Busqueda");
        for (int i = 0; i < 25; i++) {
            create(owner, prefix + " grupo " + i).andExpect(status().isCreated());
        }
        create(owner, prefix + " 100% trail").andExpect(status().isCreated());

        Set<String> seen = new HashSet<>();
        for (int page = 0; page < 2; page++) {
            JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/communities")
                            .param("query", prefix.toUpperCase() + " GRUPO").param("page", String.valueOf(page))
                            .with(auth.bearer(owner)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(body.get("totalElements").asInt()).isEqualTo(25);
            body.get("content").forEach(c -> seen.add(c.get("name").asText()));
        }
        assertThat(seen).hasSize(25);

        mockMvc.perform(get("/api/communities").param("query", prefix + " 100%").with(auth.bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(prefix + " 100% trail"));
        // "_" is a literal underscore, not "any character"
        mockMvc.perform(get("/api/communities").param("query", prefix + " _").with(auth.bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions create(UserAccount owner, String name) throws Exception {
        return mockMvc.perform(post("/api/communities")
                .with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateCommunityDto(name, "Salidas los domingos", null))));
    }

    private org.springframework.test.web.servlet.ResultActions join(UserAccount user, Community community) throws Exception {
        return mockMvc.perform(post("/api/communities/" + community.getId() + "/join").with(auth.bearer(user)));
    }
}
