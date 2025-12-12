package com.contare.printers.core;

import com.contare.printers.utils.PrinterServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PrinterConnectionTest {

    private static final int STX = 0x02;
    private static final int ETX = 0x03;
    private static final Charset chatset = StandardCharsets.UTF_8;

    private static PrinterServer server;

    @BeforeAll
    public static void setUp() throws IOException {
        server = new PrinterServer(); // open ephemeral port
    }

    @AfterAll
    public static void cleanUp() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("Read single message")
    void testReadSingleMessage() throws Exception {
        final String payload = "HELLO";
        final byte[] message = concat((byte) STX, payload.getBytes(chatset), (byte) ETX);

        // prepare a future so test can wait for server handler completion
        final CompletableFuture<Void> done = new CompletableFuture<>();

        // enqueue handler for the next accepted connection: write the message then shutdownOutput
        server.enqueueHandler((socket) -> {
            try (OutputStream out = socket.getOutputStream()) {
                out.write(message);
                out.flush();
                // ensure client receives EOF when server is done
                try {
                    socket.shutdownOutput();
                } catch (IOException ignored) {
                }
            } catch (IOException e) {
                done.completeExceptionally(e);
                return;
            }
            done.complete(null);
        });

        final int port = server.getPort();
        final PrinterConnection pc = new PrinterConnection("127.0.0.1", port);
        try {
            pc.connect(2_000);
            final List<String> results = pc.readAsString();
            assertNotNull(results);
            assertEquals(1, results.size(), "expected exactly one message");
            final String msg = results.get(0);
            final byte[] msgBytes = msg.getBytes(chatset);
            assertArrayEquals(message, msgBytes);
        } finally {
            pc.close();
        }

        // wait server handler finished (optional guard)
        done.get(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Send and receive data")
    void testSendAndServerReceivesData() throws Exception {
        final byte[] dataToSend = "PRINT THIS\n".getBytes(chatset);

        // future to capture bytes read by server handler
        final CompletableFuture<byte[]> receivedFuture = new CompletableFuture<>();

        // enqueue handler: read all bytes from client and complete the future
        server.enqueueHandler((socket) -> {
            try (InputStream in = socket.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[256];
                int r;
                while ((r = in.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
                receivedFuture.complete(baos.toByteArray());
            } catch (IOException e) {
                receivedFuture.completeExceptionally(e);
            }
        });

        final int port = server.getPort();
        final PrinterConnection pc = new PrinterConnection("127.0.0.1", port);
        try {
            pc.connect(2_000);
            pc.send(dataToSend);
            // closing client ensures server read() sees EOF
            pc.close();

            final byte[] received = receivedFuture.get(1, TimeUnit.SECONDS);
            assertArrayEquals(dataToSend, received);
        } finally {
            try {
                pc.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    @DisplayName("Read multiple messages, ignoring leading bytes")
    void testReadMultipleMessagesAndIgnoreLeadingBytes() throws Exception {
        final byte[] leading = "GARBAGE".getBytes(chatset);
        final byte[] m1 = concat((byte) STX, "ONE".getBytes(chatset), (byte) ETX);
        final byte[] m2 = concat((byte) STX, "TWO".getBytes(chatset), (byte) ETX);

        byte[] combined = new byte[leading.length + m1.length + m2.length];
        System.arraycopy(leading, 0, combined, 0, leading.length);
        System.arraycopy(m1, 0, combined, leading.length, m1.length);
        System.arraycopy(m2, 0, combined, leading.length + m1.length, m2.length);

        final CompletableFuture<Void> done = new CompletableFuture<>();

        server.enqueueHandler((socket) -> {
            try (final OutputStream out = socket.getOutputStream()) {
                out.write(combined);
                out.flush();
                socket.shutdownOutput();
            } catch (IOException e) {
                done.completeExceptionally(e);
                return;
            }
            done.complete(null);
        });

        final int port = server.getPort();
        final PrinterConnection pc = new PrinterConnection("127.0.0.1", port);
        try {
            pc.connect(2_000);
            final List<String> results = pc.readAsString();
            assertEquals(2, results.size(), "expected two messages");
            assertArrayEquals(m1, results.get(0).getBytes(chatset));
            assertArrayEquals(m2, results.get(1).getBytes(chatset));
        } finally {
            pc.close();
        }

        done.get(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Read multiple messages, including control chars")
    void testReadMultipleMessagesWithControlChars() throws Exception {
        final String text = "\u0003\u000232,PS2,RS0,RE0,PE0,EN00,BT0,Q000000\u0003\u000261,1,N,EP:3BE10000376C8DE8000022D1,ID:E280119120007624A0700360\u0003";
        final byte[] bytes = text.getBytes(chatset);

        final CompletableFuture<Void> done = new CompletableFuture<>();

        server.enqueueHandler((socket) -> {
            try (final OutputStream out = socket.getOutputStream()) {
                out.write(bytes);
                out.flush();
                socket.shutdownOutput();
            } catch (IOException e) {
                done.completeExceptionally(e);
                return;
            }
            done.complete(null);
        });

        final int port = server.getPort();
        final PrinterConnection pc = new PrinterConnection("127.0.0.1", port);
        try {
            pc.connect(2_000);
            final List<String> results = pc.readAsString();
            assertEquals(2, results.size(), "expected two messages");
            assertEquals("\u000232,PS2,RS0,RE0,PE0,EN00,BT0,Q000000\u0003", results.get(0));
            assertEquals("\u000261,1,N,EP:3BE10000376C8DE8000022D1,ID:E280119120007624A0700360\u0003", results.get(1));
        } finally {
            pc.close();
        }

        done.get(1, TimeUnit.SECONDS);
    }

    // helper to concatenate bytes with single leading and trailing bytes
    private static byte[] concat(byte lead, byte[] middle, byte trail) {
        byte[] out = new byte[1 + middle.length + 1];
        out[0] = lead;
        System.arraycopy(middle, 0, out, 1, middle.length);
        out[out.length - 1] = trail;
        return out;
    }

}
