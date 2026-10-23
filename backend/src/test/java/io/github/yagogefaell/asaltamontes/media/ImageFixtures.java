package io.github.yagogefaell.asaltamontes.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** Builds small images for the media tests. */
public final class ImageFixtures {

    /** Bytes that only exist inside the fake EXIF block: they must never reach the stored files. */
    public static final String GPS_MARKER = "GPSLatitude=42.8782N;GPSLongitude=8.5448W";

    private ImageFixtures() {
    }

    public static BufferedImage picture(int width, int height, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 80, 40));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(20, 120, 200));
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        return image;
    }

    public static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] jpeg(int width, int height) {
        return encode(picture(width, height, false), "jpeg");
    }

    public static byte[] png(int width, int height, boolean alpha) {
        return encode(picture(width, height, alpha), "png");
    }

    /**
     * A JPEG with an EXIF (APP1) block right after SOI, with the given orientation and a GPS-looking text.
     * The TIFF structure is valid so readers honour the orientation.
     */
    public static byte[] jpegWithExif(int width, int height, int orientation) {
        byte[] jpeg = jpeg(width, height);
        byte[] gps = GPS_MARKER.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0});          // little endian header, IFD0 at 8
        tiff.writeBytes(new byte[] {1, 0});                                   // one entry
        tiff.writeBytes(new byte[] {0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0}); // Orientation (SHORT)
        tiff.writeBytes(new byte[] {0, 0, 0, 0});                             // no next IFD
        tiff.writeBytes(gps);

        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        app1.writeBytes(tiff.toByteArray());
        int length = app1.size() + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.writeBytes(app1.toByteArray());
        out.writeBytes(java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));
        return out.toByteArray();
    }

    public static BufferedImage decode(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** True when the JPEG has an APP1 (EXIF/XMP) segment before the scan starts. */
    public static boolean hasApp1(byte[] jpeg) {
        int i = 2;
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xE1) {
                return true;
            }
            if (marker == 0xDA) { // start of scan: metadata segments are over
                return false;
            }
            i += 2 + (((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF));
        }
        return false;
    }

    public static boolean contains(byte[] data, String text) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(text);
    }
}
