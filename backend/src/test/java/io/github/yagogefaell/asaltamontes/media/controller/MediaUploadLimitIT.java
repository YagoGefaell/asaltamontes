package io.github.yagogefaell.asaltamontes.media.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@TestPropertySource(properties = "asaltamontes.media.uploads-per-hour=2")
class MediaUploadLimitIT extends AbstractIntegrationTest {

    @Test
    void limitsUploadsPerUser_withoutAffectingOthers() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        upload(ana).andExpect(status().isCreated());
        upload(ana).andExpect(status().isCreated());
        upload(ana).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        upload(bea).andExpect(status().isCreated());
    }

    private ResultActions upload(UserAccount user) throws Exception {
        return mockMvc.perform(multipart("/api/media")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageFixtures.jpeg(100, 100)))
                .param("purpose", "AVATAR")
                .with(auth.bearer(user)));
    }
}
