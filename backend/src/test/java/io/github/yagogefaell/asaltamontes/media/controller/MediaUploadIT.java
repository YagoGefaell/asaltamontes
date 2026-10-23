package io.github.yagogefaell.asaltamontes.media.controller;

import static io.github.yagogefaell.asaltamontes.media.ImageFixtures.GPS_MARKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class MediaUploadIT extends AbstractIntegrationTest {

    @Autowired private MediaProperties mediaProperties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void uploadsAValidJpeg_andStoresThreeResizedVariants() throws Exception {
        UserAccount ana = testData.createUser();

        MvcResult result = upload(ana, "AVATAR", "foto.jpg", "image/jpeg", ImageFixtures.jpeg(2000, 1000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        assertThat(body.get("urls").get("thumb").asText()).isEqualTo("/media/avatar/" + id + "/thumb.jpg");
        assertThat(body.get("urls").get("medium").asText()).isEqualTo("/media/avatar/" + id + "/medium.jpg");
        assertThat(body.get("urls").get("large").asText()).isEqualTo("/media/avatar/" + id + "/large.jpg");

        assertThat(sizeOf(body.get("urls").get("large").asText())).isEqualTo(new int[] {1280, 640});
        assertThat(sizeOf(body.get("urls").get("medium").asText())).isEqualTo(new int[] {640, 320});
        assertThat(sizeOf(body.get("urls").get("thumb").asText())).isEqualTo(new int[] {128, 64});

        var row = jdbcTemplate.queryForMap("SELECT owner_id, purpose, status, width, height, bytes FROM media WHERE id = ?", id);
        assertThat(row.get("owner_id")).isEqualTo(ana.getId());
        assertThat(row.get("purpose")).isEqualTo("AVATAR");
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("width")).isEqualTo(1280);
        assertThat(row.get("height")).isEqualTo(640);
        assertThat((Long) row.get("bytes")).isGreaterThan(0L);
    }

    @Test
    void doesNotEnlargeSmallImages() throws Exception {
        UserAccount ana = testData.createUser();

        JsonNode body = uploadOk(ana, "GROUP_IMAGE", "small.jpg", ImageFixtures.jpeg(300, 200));

        assertThat(sizeOf(body.get("urls").get("large").asText())).isEqualTo(new int[] {300, 200});
        assertThat(sizeOf(body.get("urls").get("medium").asText())).isEqualTo(new int[] {300, 200});
        assertThat(sizeOf(body.get("urls").get("thumb").asText())).isEqualTo(new int[] {128, 85});
    }

    @Test
    void stripsExifAndGps_fromEveryVariant() throws Exception {
        UserAccount ana = testData.createUser();
        byte[] original = ImageFixtures.jpegWithExif(800, 600, 1);
        assertThat(ImageFixtures.hasApp1(original)).isTrue();
        assertThat(ImageFixtures.contains(original, GPS_MARKER)).isTrue();

        JsonNode body = uploadOk(ana, "AVATAR", "gps.jpg", original);

        for (String variant : new String[] {"thumb", "medium", "large"}) {
            byte[] stored = Files.readAllBytes(pathOf(body.get("urls").get(variant).asText()));
            assertThat(ImageFixtures.hasApp1(stored)).as("APP1 in " + variant).isFalse();
            assertThat(ImageFixtures.contains(stored, GPS_MARKER)).as("GPS text in " + variant).isFalse();
            assertThat(ImageFixtures.contains(stored, "Exif")).as("Exif header in " + variant).isFalse();
        }
    }

    @Test
    void appliesExifOrientation_portraitPhotoStaysPortrait() throws Exception {
        UserAccount ana = testData.createUser();
        // Stored as 600x400 landscape with orientation 6 (rotate 90 clockwise): it is a portrait photo
        JsonNode body = uploadOk(ana, "AVATAR", "vertical.jpg", ImageFixtures.jpegWithExif(600, 400, 6));

        assertThat(sizeOf(body.get("urls").get("large").asText())).isEqualTo(new int[] {400, 600});
        assertThat(sizeOf(body.get("urls").get("thumb").asText())).isEqualTo(new int[] {85, 128});
    }

    @Test
    void decidesByContent_pngNamedJpgIsAccepted_andTransparencyBecomesWhite() throws Exception {
        UserAccount ana = testData.createUser();
        BufferedImage transparent = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        byte[] png = ImageFixtures.encode(transparent, "png");

        MvcResult result = upload(ana, "COMMUNITY_COVER", "me-lo-han-renombrado.jpg", "image/jpeg", png)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        BufferedImage stored = ImageFixtures.decode(Files.readAllBytes(pathOf(body.get("urls").get("large").asText())));
        int pixel = stored.getRGB(100, 100) & 0xFFFFFF;
        assertThat((pixel >> 16) & 0xFF).isGreaterThan(240);
        assertThat((pixel >> 8) & 0xFF).isGreaterThan(240);
        assertThat(pixel & 0xFF).isGreaterThan(240);
    }

    @Test
    void rejectsAPdfDeclaredAsJpeg() throws Exception {
        UserAccount ana = testData.createUser();
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

        upload(ana, "AVATAR", "foto.jpg", "image/jpeg", pdf)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El archivo no es una imagen válida (usa JPEG, PNG o WebP)"));
    }

    @Test
    void rejectsACorruptImageWithValidMagicBytes() throws Exception {
        UserAccount ana = testData.createUser();
        byte[] truncated = Arrays.copyOf(ImageFixtures.jpeg(400, 400), 40);

        upload(ana, "AVATAR", "roto.jpg", "image/jpeg", truncated).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFilesOverTenMegabytes() throws Exception {
        UserAccount ana = testData.createUser();
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(ImageFixtures.jpeg(100, 100), 0, big, 0, 100);

        upload(ana, "AVATAR", "enorme.jpg", "image/jpeg", big)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("La imagen es demasiado grande (máximo 10 MB)"));
    }

    @Test
    void rejectsImagesTooSmall_andTooBigInPixels() throws Exception {
        UserAccount ana = testData.createUser();

        upload(ana, "AVATAR", "mini.png", "image/png", ImageFixtures.png(32, 32, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen es demasiado pequeña (mínimo 64 px por lado)"));
        // Cheap to build but over the side limit: rejected from the header, nothing is decoded
        upload(ana, "AVATAR", "ancha.png", "image/png", ImageFixtures.png(8100, 100, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen es demasiado grande (máximo 8000 px por lado)"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(100, 100)))
                        .param("purpose", "AVATAR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnUnknownPurpose_andAMissingFile() throws Exception {
        UserAccount ana = testData.createUser();

        upload(ana, "PORN", "a.jpg", "image/jpeg", ImageFixtures.jpeg(100, 100)).andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/media").param("purpose", "AVATAR").with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
    }

    // ----- helpers -----

    private org.springframework.test.web.servlet.ResultActions upload(UserAccount user, String purpose, String filename,
                                                                      String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/media")
                .file(new MockMultipartFile("file", filename, contentType, content))
                .param("purpose", purpose)
                .with(auth.bearer(user))
                .accept(MediaType.APPLICATION_JSON));
    }

    private JsonNode uploadOk(UserAccount user, String purpose, String filename, byte[] content) throws Exception {
        MvcResult result = upload(user, purpose, filename, "image/jpeg", content).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** The public URL /media/x maps to the file {@code <dir>/x}. */
    private Path pathOf(String publicUrl) {
        return Path.of(mediaProperties.getDir()).resolve(publicUrl.substring("/media/".length()));
    }

    private int[] sizeOf(String publicUrl) throws Exception {
        BufferedImage image = ImageFixtures.decode(Files.readAllBytes(pathOf(publicUrl)));
        return new int[] {image.getWidth(), image.getHeight()};
    }
}
