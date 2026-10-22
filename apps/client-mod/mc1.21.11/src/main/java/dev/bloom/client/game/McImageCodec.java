package dev.bloom.client.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bloom.client.core.sky.ImageCodec;
import dev.bloom.client.core.sky.RgbaImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** PNG decoding/encoding through Minecraft's NativeImage (STB). NativeImage#getPixel/#setPixel use ARGB in this Minecraft version. */
public final class McImageCodec implements ImageCodec {
    private final Path tempDir;

    public McImageCodec(Path tempDir) {
        this.tempDir = tempDir;
    }

    @Override
    public RgbaImage decodePng(byte[] png) throws IOException {
        try (NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(png))) {
            RgbaImage out = new RgbaImage(img.getWidth(), img.getHeight());
            for (int y = 0; y < out.height; y++) {
                for (int x = 0; x < out.width; x++) out.set(x, y, img.getPixel(x, y));
            }
            return out;
        }
    }

    @Override
    public byte[] encodePng(RgbaImage image) throws IOException {
        Files.createDirectories(tempDir);
        Path tmp = Files.createTempFile(tempDir, "bloom-", ".png");
        try (NativeImage img = toNative(image)) {
            img.writeToFile(tmp);
            return Files.readAllBytes(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static NativeImage toNative(RgbaImage image) {
        NativeImage img = new NativeImage(image.width, image.height, false);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) img.setPixel(x, y, image.get(x, y));
        }
        return img;
    }

    public static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    public static int argbToAbgr(int c) {
        return abgrToArgb(c); // the swap is its own inverse
    }
}
