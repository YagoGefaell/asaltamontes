package io.github.yagogefaell.asaltamontes.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.media.ImageFixtures;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;

class ImageProcessorTest {

    private final MediaProperties properties = new MediaProperties();
    private final ImageProcessor processor = new ImageProcessor(properties);

    @Test
    void readsWebp_throughTheTwelveMonkeysPlugin() {
        // 1x1 lossless WebP
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        properties.setMinSide(1);

        ImageProcessor.Processed processed = new ImageProcessor(properties).process(webp);

        assertThat(processed.width()).isEqualTo(1);
        assertThat(ImageFixtures.decode(processed.large())).isNotNull();
    }

    /** Surefire runs with -Xmx512m (pom.xml), the production heap: the largest accepted PNG must fit in it. */
    @Test
    void processesAPngAtThePixelLimit_withTheProductionHeap() {
        assertThat(Runtime.getRuntime().maxMemory()).isLessThan(700L * 1024 * 1024);
        byte[] png = ImageFixtures.png(6000, 4000, true); // 24 Mpx = the default limit, with alpha (4 bytes per pixel)

        ImageProcessor.Processed processed = processor.process(png);

        assertThat(processed.width()).isEqualTo(1280);
        assertThat(processed.height()).isEqualTo(853);
    }

    @Test
    void rejectsRiffFilesThatAreNotWebp() {
        byte[] wav = new byte[64];
        System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);

        assertThatThrownBy(() -> processor.process(wav)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void limitsDecodedPixels_evenWhenEachSideIsAllowed() {
        properties.setMaxPixels(10_000);

        assertThatThrownBy(() -> processor.process(ImageFixtures.jpeg(200, 200))).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void readsGrayscaleImages() {
        BufferedImage gray = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);

        ImageProcessor.Processed processed = processor.process(ImageFixtures.encode(gray, "png"));

        assertThat(ImageFixtures.decode(processed.thumb()).getColorModel().getColorSpace().getType())
                .isIn(ColorSpace.TYPE_RGB, ColorSpace.TYPE_GRAY);
    }
}
