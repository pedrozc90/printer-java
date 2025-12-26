package com.contare.printers.sample.mocks;

import com.contare.printers.core.objects.RawPacket;
import com.contare.printers.sample.utils.ResourceUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SatoMockTest {

    private static SatoMock mock;

    private final ResourceUtils resources = ResourceUtils.getInstance();

    @BeforeAll
    public static void init() {
        mock = new SatoMock();
    }

    @AfterAll
    public static void close() throws IOException {
        mock.close();
    }

    @Test
    @DisplayName("Create mock")
    public void createMock() {
        assertEquals("localhost", mock.getHost());
        assertTrue(mock.getPort() != 0);
    }

    @Test
    public void parseFramedData() {
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] bytes = new byte[]{ 0x02, 0x12, 'P', 'H', 0x03 };
        final byte[] expected = { 0x12, 'P', 'H' };

        final List<RawPacket> results = mock.parseData(bytes, charset);
        assertEquals(1, results.size());

        final RawPacket result = results.get(0);
        assertEquals(3, result.length());
        assertArrayEquals(expected, result.getBytes());
    }

    @Test
    public void parseNotFramedData() {
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] bytes = new byte[]{ 0x12, 'P', 'H' };

        final List<RawPacket> results = mock.parseData(bytes, charset);
        assertEquals(1, results.size());

        final RawPacket result = results.get(0);
        assertEquals(3, result.length());
        assertArrayEquals(bytes, result.getBytes());
    }

    @Test
    public void test() throws IOException {
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] bytes = resources.getAsBytes("files/SBPL.txt");

        final List<RawPacket> results = mock.parseData(bytes, charset);
        assertEquals(3, results.size());
    }

}
