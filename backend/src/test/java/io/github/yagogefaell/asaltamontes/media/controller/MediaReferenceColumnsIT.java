package io.github.yagogefaell.asaltamontes.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * Guard for the orphan cleanup: every column that references a media must be considered by
 * {@code MediaRepository.isReferenced} and {@code findUnreferencedCreatedBefore}, or the cleanup would delete
 * images that are in use. A new {@code *_media_id} column fails the first test until it is added to both queries
 * (and to the list here).
 */
class MediaReferenceColumnsIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private MediaCleanupService cleanupService;

    @Test
    void everyMediaReferenceColumnInTheSchemaIsKnown() {
        List<String> columns = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name LIKE '%\\_media\\_id' ORDER BY 1
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "user_profiles.avatar_media_id", "communities.cover_media_id", "conversations.group_media_id");
    }

    @Test
    void communityCoverAndGroupImage_areNotCollectedWhileReferenced() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID cover = upload(ana, "COMMUNITY_COVER");
        UUID groupImage = upload(ana, "GROUP_IMAGE");
        mockMvc.perform(post("/api/communities").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Guard " + System.nanoTime(), "coverMediaId", cover.toString()))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/conversations/group").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("groupName", "G", "groupMediaId", groupImage.toString(),
                                "users", List.of(bea.getUsername())))))
                .andExpect(status().isOk());
        for (UUID id : List.of(cover, groupImage)) {
            jdbc.update("UPDATE media SET created_at = now() - interval '3 days' WHERE id = ?", id);
            assertThat(mediaRepository.isReferenced(id)).isTrue();
            assertThat(cleanupService.deleteIfUnreferenced(id, null)).isFalse();
        }
        cleanupService.deleteOrphans();
        assertThat(mediaRepository.existsById(cover)).isTrue();
        assertThat(mediaRepository.existsById(groupImage)).isTrue();
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
