package io.github.yagogefaell.asaltamontes.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.media.domain.Media;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.domain.MediaStatus;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;

/** T17c: references by media id, replacement of pictures, orphan cleanup and storage quota. */
class MediaReferencesIT extends AbstractIntegrationTest {

    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private UserProfileRepository profileRepository;
    @Autowired
    private MediaCleanupService cleanupService;
    @Autowired
    private MediaReferenceService referenceService;
    @Autowired
    private MediaProperties properties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // ----- references -----

    @Test
    void avatar_byMediaId_storesTheReferenceAndTheMediumUrl() throws Exception {
        UserAccount ana = testData.createUser();
        UUID media = upload(ana, "AVATAR");

        patchProfile(ana, Map.of("avatarMediaId", media.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value("/media/avatar/" + media + "/medium.jpg"));

        assertThat(profileRepository.findById(ana.getId()).orElseThrow().getAvatarMediaId()).isEqualTo(media);
    }

    @Test
    void avatar_mediaIdOfAnotherUserOrWrongPurpose_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID anasAvatar = upload(ana, "AVATAR");
        UUID beasCover = upload(bea, "COMMUNITY_COVER");

        patchProfile(bea, Map.of("avatarMediaId", anasAvatar.toString())).andExpect(status().isNotFound());
        patchProfile(bea, Map.of("avatarMediaId", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        patchProfile(bea, Map.of("avatarMediaId", beasCover.toString())).andExpect(status().isBadRequest());
        assertThat(profileRepository.findById(bea.getId()).orElseThrow().getAvatarMediaId()).isNull();
    }

    @Test
    void avatar_internalUrlIsConvertedToAReference() throws Exception {
        UserAccount ana = testData.createUser();
        UUID media = upload(ana, "AVATAR");

        patchProfile(ana, Map.of("profilePictureUrl", "https://host.example/media/avatar/" + media + "/thumb.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value("/media/avatar/" + media + "/medium.jpg"));
        assertThat(profileRepository.findById(ana.getId()).orElseThrow().getAvatarMediaId()).isEqualTo(media);
    }

    @Test
    void avatar_newExternalUrlsAreRejected_butAnUnchangedLegacyOneIsKept() throws Exception {
        UserAccount ana = testData.createUser();
        jdbc.update("UPDATE user_profiles SET profile_picture_url = ? WHERE user_id = ?",
                "https://lh3.googleusercontent.com/a/legacy", ana.getId());

        patchProfile(ana, Map.of("profilePictureUrl", "https://tracker.example/pixel.png")).andExpect(status().isBadRequest());
        patchProfile(ana, Map.of("profilePictureUrl", "https://lh3.googleusercontent.com/a/legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value("https://lh3.googleusercontent.com/a/legacy"));
        // A legacy value with characters the strict filter rejects is still accepted when resent unchanged
        String awkward = "https://lh3.googleusercontent.com/a/x%3Dy..z=s96-c";
        jdbc.update("UPDATE user_profiles SET profile_picture_url = ? WHERE user_id = ?", awkward, ana.getId());
        patchProfile(ana, Map.of("profilePictureUrl", awkward))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value(awkward));
        patchProfile(ana, Map.of("profilePictureUrl", awkward + "2")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE user_profiles SET profile_picture_url = ? WHERE user_id = ?",
                "https://lh3.googleusercontent.com/a/legacy", ana.getId());
        // Other fields do not touch the picture
        patchProfile(ana, Map.of("bio", "Hola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value("https://lh3.googleusercontent.com/a/legacy"));
    }

    @Test
    void urlFieldsOverTheColumnLengthAreA400NotA500() throws Exception {
        UserAccount ana = testData.createUser();
        String longUrl = "https://example.com/a.png?x=" + "a".repeat(300);

        patchProfile(ana, Map.of("profilePictureUrl", longUrl)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/communities").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Larga " + System.nanoTime(), "imageUrl", longUrl))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/conversations/group").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("groupName", "Grupo", "groupImage", longUrl, "users", List.of("x")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/conversations/group").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("groupName", "g".repeat(81), "users", List.of("x")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void community_andGroup_byMediaId() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID cover = upload(ana, "COMMUNITY_COVER");
        UUID groupImage = upload(ana, "GROUP_IMAGE");

        mockMvc.perform(post("/api/communities").with(auth.bearer(bea)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Robada " + System.nanoTime(), "coverMediaId", cover.toString()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/communities").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Propia " + System.nanoTime(), "coverMediaId", cover.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("/media/community-cover/" + cover + "/medium.jpg"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communities WHERE cover_media_id = ?", Long.class, cover)).isEqualTo(1);

        createGroup(bea, Map.of("groupMediaId", groupImage.toString())).andExpect(status().isNotFound());
        createGroup(ana, Map.of("groupMediaId", groupImage.toString(), "users", List.of(bea.getUsername())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE group_media_id = ?", Long.class, groupImage)).isEqualTo(1);
    }

    // ----- replacing / removing -----

    @Test
    void replacingTheAvatar_deletesThePreviousImageFilesAndRow() throws Exception {
        UserAccount ana = testData.createUser();
        UUID first = upload(ana, "AVATAR");
        UUID second = upload(ana, "AVATAR");
        patchProfile(ana, Map.of("avatarMediaId", first.toString())).andExpect(status().isOk());
        assertThat(filesOf("avatar", first)).isTrue();

        patchProfile(ana, Map.of("avatarMediaId", second.toString())).andExpect(status().isOk());

        assertThat(mediaRepository.existsById(first)).isFalse();
        assertThat(filesOf("avatar", first)).isFalse();
        assertThat(mediaRepository.existsById(second)).isTrue();
        assertThat(filesOf("avatar", second)).isTrue();
    }

    @Test
    void sendingTheSameAvatarAgain_doesNotDeleteIt() throws Exception {
        UserAccount ana = testData.createUser();
        UUID media = upload(ana, "AVATAR");
        patchProfile(ana, Map.of("avatarMediaId", media.toString())).andExpect(status().isOk());
        patchProfile(ana, Map.of("profilePictureUrl", "/media/avatar/" + media + "/medium.jpg")).andExpect(status().isOk());

        assertThat(mediaRepository.existsById(media)).isTrue();
        assertThat(filesOf("avatar", media)).isTrue();
    }

    @Test
    void removingTheAvatar_deletesTheImage() throws Exception {
        UserAccount ana = testData.createUser();
        UUID media = upload(ana, "AVATAR");
        patchProfile(ana, Map.of("avatarMediaId", media.toString())).andExpect(status().isOk());

        patchProfile(ana, Map.of("profilePictureUrl", "")).andExpect(status().isOk());

        assertThat(mediaRepository.existsById(media)).isFalse();
        assertThat(filesOf("avatar", media)).isFalse();
        assertThat(profileRepository.findById(ana.getId()).orElseThrow().getProfilePictureUrl()).isNull();
    }

    // ----- orphan cleanup -----

    @Test
    void cleanup_deletesOldOrphans_andKeepsRecentOrReferencedOnes() throws Exception {
        UserAccount ana = testData.createUser();
        UUID oldOrphan = upload(ana, "AVATAR");
        UUID oldReferenced = upload(ana, "AVATAR");
        UUID recentOrphan = upload(ana, "AVATAR");
        patchProfile(ana, Map.of("avatarMediaId", oldReferenced.toString())).andExpect(status().isOk());
        backdate(oldOrphan, Duration.ofHours(25));
        backdate(oldReferenced, Duration.ofHours(25));
        backdate(recentOrphan, Duration.ofHours(23));

        int deleted = cleanupService.deleteOrphans();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(mediaRepository.existsById(oldOrphan)).isFalse();
        assertThat(filesOf("avatar", oldOrphan)).isFalse();
        assertThat(mediaRepository.existsById(oldReferenced)).isTrue();
        assertThat(filesOf("avatar", oldReferenced)).isTrue();
        assertThat(mediaRepository.existsById(recentOrphan)).isTrue();
        assertThat(filesOf("avatar", recentOrphan)).isTrue();
    }

    @Test
    void cleanup_doesNotDeleteAMediaThatIsBeingLinked() throws Exception {
        UserAccount ana = testData.createUser();
        UUID media = upload(ana, "AVATAR");
        backdate(media, Duration.ofHours(48)); // an orphan old enough to be collected
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch linked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> linking = pool.submit(() -> tx.executeWithoutResult(status -> {
                MediaReferenceService.Linked l = referenceService.link(media, null, MediaPurpose.AVATAR, ana.getId(), null);
                var profile = profileRepository.findById(ana.getId()).orElseThrow();
                profile.setAvatarMediaId(l.mediaId());
                profile.setProfilePictureUrl(l.url());
                profileRepository.saveAndFlush(profile);
                linked.countDown();
                await(commit);
            }));
            assertThat(linked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> cleaning = pool.submit(() -> cleanupService.deleteIfUnreferenced(media, null));
            Thread.sleep(500);
            assertThat(cleaning.isDone()).as("cleanup waits for the linking transaction").isFalse();

            commit.countDown();
            linking.get(10, TimeUnit.SECONDS);
            assertThat(cleaning.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
        assertThat(mediaRepository.existsById(media)).isTrue();
        assertThat(profileRepository.findById(ana.getId()).orElseThrow().getAvatarMediaId()).isEqualTo(media);
    }

    // ----- quota -----

    @Test
    void upload_overTheStorageQuota_isRejectedInSpanish() throws Exception {
        UserAccount ana = testData.createUser();
        Media filler = new Media();
        filler.setId(UUID.randomUUID());
        filler.setOwnerId(ana.getId());
        filler.setPurpose(MediaPurpose.AVATAR);
        filler.setStatus(MediaStatus.READY);
        filler.setWidth(1);
        filler.setHeight(1);
        filler.setBytes(properties.getMaxBytesPerUser().toBytes());
        mediaRepository.save(filler);

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(400, 300)))
                        .param("purpose", "AVATAR")
                        .with(auth.bearer(ana)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("límite de almacenamiento")));

        // Another user is not affected
        upload(testData.createUser(), "AVATAR");
    }

    @Test
    void upload_whichWouldCrossTheQuota_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        Media filler = new Media();
        filler.setId(UUID.randomUUID());
        filler.setOwnerId(ana.getId());
        filler.setPurpose(MediaPurpose.AVATAR);
        filler.setStatus(MediaStatus.READY);
        filler.setWidth(1);
        filler.setHeight(1);
        filler.setBytes(properties.getMaxBytesPerUser().toBytes() - 10); // 10 bytes left: no image fits
        mediaRepository.save(filler);

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(400, 300)))
                        .param("purpose", "AVATAR")
                        .with(auth.bearer(ana)))
                .andExpect(status().isConflict());
    }

    // ----- helpers -----

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void backdate(UUID media, Duration age) {
        jdbc.update("UPDATE media SET created_at = now() - (? * interval '1 second') WHERE id = ?", age.toSeconds(), media);
    }

    private boolean filesOf(String segment, UUID media) {
        Path folder = Path.of(properties.getDir()).resolve(segment).resolve(media.toString());
        return Files.exists(folder.resolve("medium.jpg"));
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

    private ResultActions patchProfile(UserAccount user, Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch("/api/users/me")
                .with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions createGroup(UserAccount creator, Map<String, ?> extra) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(extra);
        body.put("groupName", "Grupo");
        body.putIfAbsent("users", List.of("nadie"));
        return mockMvc.perform(post("/api/conversations/group")
                .with(auth.bearer(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }
}
