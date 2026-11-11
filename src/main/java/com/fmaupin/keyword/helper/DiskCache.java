/*
 * Keyword Extractor
 *
 * Copyright (C) 2026 Fabrice Maupin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.fmaupin.keyword.helper;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.*;

public class DiskCache {
    private final Path root = Path.of("cache/embeddings");

    public DiskCache() throws java.io.IOException {
        Files.createDirectories(root);
    }

    public boolean exists(String key) {
        return Files.exists(root.resolve(key));
    }

    public float[] load(String key) throws java.io.IOException {
        ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(root.resolve(key)));
        float[] v = new float[bb.remaining() / 4];
        for (int i = 0; i < v.length; i++)
            v[i] = bb.getFloat();
        return v;
    }

    public void save(String key, float[] v) throws java.io.IOException {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4);
        for (float f : v)
            bb.putFloat(f);
        Files.write(root.resolve(key), bb.array());
    }

    public static String hash(String s) throws java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(s.getBytes()));
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b)
            sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
