package io.github.yagogefaell.asaltamontes.media.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T17b: an image can only be linked to a resource by the user who uploaded it, for the right purpose. */
class MediaLinkingIT extends AbstractIntegrationTest {

    @Test
    void avatar_ownMediaWithRightPurpose_isAccepted() throws Exception {
        UserAccount ana = testData.createUser();
        String url = uploadAndGetMediumUrl(ana, "AVATAR");

        patchProfile(ana, url)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value(url));
    }

    @Test
    void avatar_anotherUsersMedia_isNotFound() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String anasUrl = uploadAndGetMediumUrl(ana, "AVATAR");

        patchProfile(bea, anasUrl).andExpect(status().isNotFound());
        // Same answer for an id that does not exist, so ids cannot be probed
        patchProfile(bea, "/media/avatar/" + UUID.randomUUID() + "/medium.jpg").andExpect(status().isNotFound());
        // Absolute URL with our path does not bypass the check
        patchProfile(bea, "https://evil.example" + anasUrl).andExpect(status().isNotFound());
    }

    @Test
    void avatar_ownMediaWithWrongPurpose_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        String coverUrl = uploadAndGetMediumUrl(ana, "COMMUNITY_COVER");

        patchProfile(ana, coverUrl).andExpect(status().isBadRequest());
    }

    @Test
    void avatar_externalUrlsStillWork() throws Exception {
        UserAccount ana = testData.createUser();

        patchProfile(ana, "https://example.com/foto.jpg").andExpect(status().isOk());
    }

    @Test
    void community_anotherUsersCover_isNotFound_andOwnCoverIsAccepted() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String anasCover = uploadAndGetMediumUrl(ana, "COMMUNITY_COVER");

        createCommunity(bea, "Trail Bea", anasCover).andExpect(status().isNotFound());
        createCommunity(ana, "Trail Ana", anasCover)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(anasCover));
    }

    @Test
    void community_wrongPurpose_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        String avatar = uploadAndGetMediumUrl(ana, "AVATAR");

        createCommunity(ana, "Trail Ana", avatar).andExpect(status().isBadRequest());
    }

    @Test
    void group_anotherUsersImage_isNotFound_andOwnImageIsAccepted() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount carla = testData.createUser();
        String anasImage = uploadAndGetMediumUrl(ana, "GROUP_IMAGE");

        createGroup(bea, anasImage, carla).andExpect(status().isNotFound());
        createGroup(ana, anasImage, carla).andExpect(status().isOk());
    }

    @Test
    void group_wrongPurpose_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String avatar = uploadAndGetMediumUrl(ana, "AVATAR");

        createGroup(ana, avatar, bea).andExpect(status().isBadRequest());
    }

    @Test
    void pathTricksCannotSmuggleAnotherUsersMedia() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String mine = uploadAndGetMediumUrl(bea, "COMMUNITY_COVER");
        String anas = uploadAndGetMediumUrl(ana, "COMMUNITY_COVER");
        String myId = mine.split("/")[3];
        String otherPath = anas; // /media/community-cover/<ana>/medium.jpg

        List<String> attacks = List.of(
                "/media/community-cover/" + myId + "/../../.." + otherPath,
                "/media/../media/community-cover/" + anas.split("/")[3] + "/medium.jpg",
                "/./media/community-cover/" + anas.split("/")[3] + "/medium.jpg",
                "/%6dedia/community-cover/" + anas.split("/")[3] + "/medium.jpg",
                "/media/community-cover/%2e%2e/" + anas.split("/")[3] + "/medium.jpg",
                "\\media\\community-cover\\" + anas.split("/")[3] + "\\medium.jpg",
                "//evil.example" + otherPath,
                "/media/community-cover/" + anas.split("/")[3] + "/medium.jpg\n",
                "/media/community-cover/" + anas.split("/")[3] + "/me\tdium.jpg",
                "/media/community-cover/" + anas.split("/")[3] + "/medium.jpg\r\nX: y",
                "/media/community-cover/" + anas.split("/")[3] + "/medium.jpg?x=1",
                "/media/community-cover/" + anas.split("/")[3].toUpperCase() + "/medium.jpg",
                "https://evil.example/x/../media/community-cover/" + anas.split("/")[3] + "/medium.jpg",
                "https://evil.example/media/community-cover/" + myId + "/..%2f" + anas.split("/")[3] + "/medium.jpg",
                "foto.jpg");
        int n = 0;
        for (String attack : attacks) {
            createCommunity(bea, "Trail Bea " + n++, attack).andExpect(status().is4xxClientError());
        }
        // None of them created a community
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/communities")
                        .param("query", "Trail Bea").with(auth.bearer(bea)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pathTricksAreRejectedInProfileAndGroupToo() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String anasId = uploadAndGetMediumUrl(ana, "AVATAR").split("/")[3];

        patchProfile(bea, "https://evil.example/x/../media/avatar/" + anasId + "/medium.jpg")
                .andExpect(status().is4xxClientError());
        patchProfile(bea, "/media/avatar/" + anasId + "/../../avatar/" + anasId + "/medium.jpg")
                .andExpect(status().is4xxClientError());
        createGroup(bea, "/media/../media/group-image/" + anasId + "/medium.jpg", ana)
                .andExpect(status().is4xxClientError());
        createGroup(bea, "uploads/foto.jpg", ana).andExpect(status().isBadRequest());
    }

    // ----- helpers -----

    private String uploadAndGetMediumUrl(UserAccount user, String purpose) throws Exception {
        String body = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(400, 300)))
                        .param("purpose", purpose)
                        .with(auth.bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("urls").get("medium").asText();
    }

    private ResultActions patchProfile(UserAccount user, String pictureUrl) throws Exception {
        return mockMvc.perform(patch("/api/users/me")
                .with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("profilePictureUrl", pictureUrl))));
    }

    private ResultActions createCommunity(UserAccount user, String name, String imageUrl) throws Exception {
        return mockMvc.perform(post("/api/communities")
                .with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "imageUrl", imageUrl))));
    }

    private ResultActions createGroup(UserAccount creator, String imageUrl, UserAccount member) throws Exception {
        return mockMvc.perform(post("/api/conversations/group")
                .with(auth.bearer(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("groupName", "Grupo", "groupImage", imageUrl,
                        "users", List.of(member.getUsername())))));
    }
}
