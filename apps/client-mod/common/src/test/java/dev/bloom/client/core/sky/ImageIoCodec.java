package dev.bloom.client.core.sky;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Test codec using the JDK (the game uses Minecraft's NativeImage). */
public final class ImageIoCodec implements ImageCodec {
    @Override
    public RgbaImage decodePng(byte[] png) throws IOException {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(png));
        if (bi == null) throw new IOException("not an image");
        RgbaImage out = new RgbaImage(bi.getWidth(), bi.getHeight());
        bi.getRGB(0, 0, bi.getWidth(), bi.getHeight(), out.argb, 0, bi.getWidth());
        return out;
    }

    @Override
    public byte[] encodePng(RgbaImage image) throws IOException {
        BufferedImage bi = new BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, image.width, image.height, image.argb, 0, image.width);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        return bos.toByteArray();
    }

    public static byte[] png(int w, int h, int argb) throws IOException {
        RgbaImage img = new RgbaImage(w, h);
        java.util.Arrays.fill(img.argb, argb);
        return new ImageIoCodec().encodePng(img);
    }
}
