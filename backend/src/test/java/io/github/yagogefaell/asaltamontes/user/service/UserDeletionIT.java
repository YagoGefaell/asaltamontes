package io.github.yagogefaell.asaltamontes.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** BUG-064: deleting an account removes its uploaded images (rows and files). */
class UserDeletionIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private MediaProperties properties;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deleteUser_removesTheMediaRowsAndFiles() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID avatar = upload(ana, "AVATAR");
        UUID unlinked = upload(ana, "GROUP_IMAGE");
        UUID beasAvatar = upload(bea, "AVATAR");
        mockMvc.perform(patch("/api/users/me").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("avatarMediaId", avatar.toString())))).andExpect(status().isOk());
        assertThat(filesExist(avatar)).isTrue();
        assertThat(filesExist(unlinked)).isTrue();

        userService.deleteUser(ana.getId());

        assertThat(count("SELECT count(*) FROM users WHERE id = ?", ana.getId())).isZero();
        assertThat(count("SELECT count(*) FROM media WHERE owner_id = ?", ana.getId())).isZero();
        assertThat(filesExist(avatar)).isFalse();
        assertThat(filesExist(unlinked)).isFalse();
        // Other users' pictures are untouched
        assertThat(count("SELECT count(*) FROM media WHERE id = ?", beasAvatar)).isEqualTo(1);
        assertThat(filesExist(beasAvatar)).isTrue();
    }

    @Test
    void deleteUser_detachesTheirMediaFromResourcesThatSurvive() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Community community = testData.createCommunity(bea);
        UUID cover = upload(ana, "COMMUNITY_COVER");
        // Cannot happen through the API (ownership check); simulates a reference left by other means
        jdbc.update("UPDATE communities SET cover_media_id = ?, image_url = ? WHERE id = ?", cover,
                "/media/community-cover/" + cover + "/medium.jpg", community.getId());

        userService.deleteUser(ana.getId());

        assertThat(count("SELECT count(*) FROM media WHERE id = ?", cover)).isZero();
        assertThat(jdbc.queryForObject("SELECT cover_media_id FROM communities WHERE id = ?", UUID.class,
                community.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT image_url FROM communities WHERE id = ?", String.class,
                community.getId())).isNull();
        assertThat(filesExist(cover)).isFalse();
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }

    /** Every variant lives in a folder named after the media id, whatever the purpose. */
    private boolean filesExist(UUID media) throws Exception {
        try (var files = Files.walk(Path.of(properties.getDir()))) {
            return files.anyMatch(p -> p.getFileName().toString().equals("medium.jpg")
                    && p.getParent().getFileName().toString().equals(media.toString()));
        }
    }

    private UUID upload(UserAccount user, String purpose) throws Exception {
        String body = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(400, 300)))
                        .param("purpose", purpose)
                        .with(auth.bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}
