package dev.bloom.client.core.sky;

import java.io.IOException;

/** PNG decode/encode, provided by the game layer (Minecraft's NativeImage) or by tests. */
public interface ImageCodec {
    RgbaImage decodePng(byte[] png) throws IOException;

    byte[] encodePng(RgbaImage image) throws IOException;
}
