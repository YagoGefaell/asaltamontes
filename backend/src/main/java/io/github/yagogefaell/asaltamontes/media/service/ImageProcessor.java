package io.github.yagogefaell.asaltamontes.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Validates an uploaded image by its content and re-encodes it as JPEG in three sizes. Decoding and re-encoding
 * drops everything that was not pixels: EXIF (GPS included), XMP, comments and any appended payload.
 * Callers must not run several at once (memory): see {@link MediaService}.
 */
@Component
@RequiredArgsConstructor
public class ImageProcessor {

    static final int THUMB = 128;
    static final int MEDIUM = 640;
    static final int LARGE = 1280;
    private static final float JPEG_QUALITY = 0.85f;

    private final MediaProperties properties;

    public record Processed(byte[] thumb, byte[] medium, byte[] large, int width, int height) {
        public long totalBytes() {
            return (long) thumb.length + medium.length + large.length;
        }
    }

    private enum Format {
        JPEG("jpeg"), PNG("png"), WEBP("webp");

        final String readerName;

        Format(String readerName) {
            this.readerName = readerName;
        }
    }

    public Processed process(byte[] data) {
        Format format = detectFormat(data);
        int longest = checkDimensions(data, format);

        BufferedImage large;
        try {
            // Applies the EXIF orientation and shrinks (never enlarges) in one go
            large = flatten(fit(Thumbnails.of(new ByteArrayInputStream(data)), longest));
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("No se ha podido leer la imagen; prueba con otra");
        }
        BufferedImage medium = flatten(resize(large, MEDIUM));
        BufferedImage thumb = flatten(resize(large, THUMB));

        return new Processed(encode(thumb), encode(medium), encode(large), large.getWidth(), large.getHeight());
    }

    /** By magic bytes: neither the file name nor the declared Content-Type are trusted. */
    private static Format detectFormat(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return Format.PNG;
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return Format.WEBP;
        }
        throw new InvalidImageException("El archivo no es una imagen válida (usa JPEG, PNG o WebP)");
    }

    /** Reads only the header (returns the longest side), so a decompression bomb is rejected before any pixel is decoded. */
    private int checkDimensions(byte[] data, Format format) {
        int width;
        int height;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
            if (in == null || !readers.hasNext()) {
                throw new InvalidImageException("El archivo no es una imagen válida (usa JPEG, PNG o WebP)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw new InvalidImageException("No se ha podido leer la imagen; prueba con otra");
        }

        if (Math.min(width, height) < properties.getMinSide()) {
            throw new InvalidImageException("La imagen es demasiado pequeña (mínimo " + properties.getMinSide() + " px por lado)");
        }
        if (Math.max(width, height) > properties.getMaxSide() || (long) width * height > properties.getMaxPixels()) {
            throw new InvalidImageException("La imagen es demasiado grande (máximo " + properties.getMaxSide() + " px por lado)");
        }
        return Math.max(width, height);
    }

    private static BufferedImage fit(Thumbnails.Builder<? extends java.io.InputStream> builder, int longestSide) throws IOException {
        // Thumbnailator would enlarge small images, so those are only decoded (and oriented)
        return (longestSide > LARGE ? builder.size(LARGE, LARGE) : builder.scale(1.0)).asBufferedImage();
    }

    private static BufferedImage resize(BufferedImage source, int maxSide) {
        if (Math.max(source.getWidth(), source.getHeight()) <= maxSide) {
            return source;
        }
        try {
            return Thumbnails.of(source).size(maxSide, maxSide).asBufferedImage();
        } catch (IOException e) {
            throw new IllegalStateException("Could not resize image", e);
        }
    }

    /** JPEG has no alpha: transparent areas become white. Also normalizes any colour model to plain RGB. */
    private static BufferedImage flatten(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** JPEG without metadata (null metadata: the writer adds only what the format needs). */
    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        } finally {
            writer.dispose();
        }
    }
}
