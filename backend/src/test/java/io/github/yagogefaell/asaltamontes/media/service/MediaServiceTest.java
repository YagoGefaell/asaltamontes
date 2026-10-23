package io.github.yagogefaell.asaltamontes.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.RateLimiter;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.storage.MediaStorage;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class MediaServiceTest {

    @Test
    void aRequestWaitingForASlot_isRejectedWithoutReadingItsUpload() throws Exception {
        MediaProperties properties = new MediaProperties();
        properties.setMaxConcurrentProcessing(1);
        properties.setProcessingWaitSeconds(0);
        ImageProcessor processor = mock(ImageProcessor.class);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(processor.process(any())).thenAnswer(call -> {
            inside.countDown();
            release.await(10, TimeUnit.SECONDS);
            throw new InvalidImageException("stop here");
        });
        MediaService service = new MediaService(properties, processor, mock(MediaStorage.class),
                mock(MediaRepository.class), new RateLimiter());
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(1L);

        MultipartFile first = upload();
        MultipartFile second = upload();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = executor.submit(() -> service.upload(user, MediaPurpose.AVATAR, first));
            assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.upload(user, MediaPurpose.AVATAR, second))
                    .isInstanceOf(TooManyRequestsException.class);
            verify(second, never()).getInputStream();
            verify(second, never()).getBytes();

            release.countDown();
            assertThatThrownBy(running::get).hasCauseInstanceOf(InvalidImageException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static MultipartFile upload() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getInputStream()).thenAnswer(call -> new ByteArrayInputStream(new byte[10]));
        return file;
    }
}
