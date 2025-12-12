package com.contare.printers.zebra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ResourceHelper {

    private final ClassLoader loader = getClass().getClassLoader();

    public String getAsString(final String filename) throws IOException {
        try (final InputStream stream = loader.getResourceAsStream(filename)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + filename);
            }

            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;

            while ((length = stream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            return result.toString(StandardCharsets.UTF_8.name());
        }
    }

    public byte[] getAsBytes(final String filename) throws IOException {
        try (final InputStream stream = loader.getResourceAsStream(filename)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + filename);
            }

            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;

            while ((length = stream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            return result.toByteArray();
        }
    }

}
