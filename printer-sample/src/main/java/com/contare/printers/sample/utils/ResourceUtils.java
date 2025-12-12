package com.contare.printers.sample.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class ResourceUtils {

    private static final int EOF = -1;

    private static ResourceUtils instance;

    private final ClassLoader loader;

    public ResourceUtils() {
        this.loader = getClass().getClassLoader();
    }

    public static ResourceUtils getInstance() {
        if (instance == null) {
            instance = new ResourceUtils();
        }
        return instance;
    }

    public byte[] getAsBytes(final String filename) throws IOException {
        try (final InputStream stream = loader.getResourceAsStream(filename)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + filename);
            }

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024];

            int read;
            while ((read = stream.read(bytes)) != EOF) {
                out.write(bytes, 0, read);
            }

            return out.toByteArray();
        }
    }

    public String getAsString(final String filename, final Charset charset) throws IOException {
        final byte[] bytes = getAsBytes(filename);
        return new String(bytes, charset);
    }

}
