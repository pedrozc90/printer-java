package com.contare.printers.sample.mocks;

import com.contare.printers.core.objects.ControlCmd;
import com.contare.printers.core.objects.RawPacket;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class BaseMock implements AutoCloseable {

    protected final Logger logger;
    protected final ExecutorService clients = Executors.newCachedThreadPool();

    protected final Charset charset;
    protected ServerSocket server;
    protected Thread thread;

    public BaseMock(final Charset charset) {
        this.logger = Logger.getLogger(getClass());
        this.charset = charset;
        init();
    }

    /**
     * Returns the host name of the bound socket (useful when you want to connect to it from another JVM).
     */
    public String getHost() {
        if (server == null) return "localhost";
        return server.getInetAddress().getHostAddress();
    }

    /**
     * Returns the actual bound port (useful when you requested port 0).
     */
    public int getPort() {
        if (server == null) return 0;
        return server.getLocalPort();
    }

    private void init() {
        try {
            server = new ServerSocket(0);
            server.setReuseAddress(true);

            final Acceptor acceptor = new Acceptor(server, charset);
            thread = new Thread(acceptor, "sato-mock-acceptor");
            thread.setDaemon(true);
            thread.start();

            logger.debugf("Listening on port %d", getPort());
        } catch (IOException e) {
            logger.error("Error starting server", e);
        }
    }

    @Override
    public void close() throws IOException {
        logger.debugf("Stopping...");

        try {
            if (server != null && !server.isClosed()) {
                try {
                    server.close();
                } finally {
                    server = null;
                }
            }
        } catch (IOException e) {
            logger.error("Error stopping server", e);
        }

        clients.shutdownNow();

        try {
            final boolean terminated = clients.awaitTermination(500, TimeUnit.MILLISECONDS);
            if (terminated) {
                logger.debugf("Client executor terminated");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }

        logger.debugf("Stopped");
    }

    public abstract RawPacket onMessage(final RawPacket packet) throws IOException;

    private class Acceptor implements Runnable {

        private final ServerSocket socket;
        private final Charset charset;

        Acceptor(final ServerSocket socket, final Charset charset) {
            this.socket = socket;
            this.charset = charset;
        }

        @Override
        public void run() {
            try {
                while (!socket.isClosed()) {
                    final Socket client = socket.accept();
                    logger.debugf("Accepted client %s", client.getRemoteSocketAddress());

                    final Runnable handler = new MockRunnable(client, charset);
                    clients.execute(handler);
                }
            } catch (IOException e) {
                if (socket.isClosed()) {
                    // normal shutdown
                } else {
                    logger.error("Accept error", e);
                }
            }
        }
    }

    public class MockRunnable implements Runnable {

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final Charset charset;

        public MockRunnable(final Socket socket, final Charset charset) throws IOException {
            this.socket = socket;
            this.charset = charset;
            this.socket.setSoTimeout(0); // blocking
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        @Override
        public void run() {
            try {
                byte[] tmp = new byte[512];
                int read;

                // read loop - process commands as they arrive
                while ((read = in.read(tmp)) != ControlCmd.EOF) {
                    if (read > 0) {
                        byte[] data = Arrays.copyOf(tmp, read);
                        processData(data);
                    }
                }
            } catch (IOException e) {
                // connection closed or error
                logger.error("Client disconnected", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }

        /**
         * Process received data and extract commands
         */
        private void processData(final byte[] data) throws IOException {
            logger.debugf("Received data: %s", Arrays.toString(data));

            final List<RawPacket> packets = parseCommands(data);

            for (RawPacket packet : packets) {
                logger.infof("Processing command: %s", packet);
                handleCommand(packet);
            }
        }

        /**
         * Parse commands from raw bytes, handling both framed (STX... ETX) and unframed messages
         */
        private List<RawPacket> parseCommands(final byte[] data) {
            final List<RawPacket> out = new ArrayList<>();

            int i = 0;
            while (i < data.length) {
                // Check if this is a framed message (starts with STX)
                if (data[i] == ControlCmd.STX) {
                    // Find the ETX
                    int etxIndex = -1;
                    for (int j = i + 1; j < data.length; j++) {
                        if (data[j] == ControlCmd.ETX) {
                            etxIndex = j;
                            break;
                        }
                    }

                    if (etxIndex != -1) {
                        // Extract framed content (between STX and ETX)
                        byte[] framedContent = Arrays.copyOfRange(data, i + 1, etxIndex);
                        extractCommandsFromBytes(framedContent, out);
                        i = etxIndex + 1;
                    } else {
                        // No ETX found, treat rest as content
                        byte[] content = Arrays.copyOfRange(data, i + 1, data.length);
                        extractCommandsFromBytes(content, out);
                        break;
                    }
                } else {
                    // Unframed message - extract commands from current position
                    byte[] content = Arrays.copyOfRange(data, i, data.length);
                    extractCommandsFromBytes(content, out);
                    break;
                }
            }

            return out;
        }

        /**
         * Extract individual commands from bytes (handles multi-byte commands like DC2+PG)
         */
        private void extractCommandsFromBytes(final byte[] bytes, final List<RawPacket> out) {
            int i = 0;

            while (i < bytes.length) {
                byte current = bytes[i];

                // Handle DC2 (0x12) commands. Prefer DC2 + two-letter commands (e.g. DC2 + 'P' 'H').
                if (current == ControlCmd.DC2) {
                    // If there's room for two letters, and they look like letters, consume both.
                    if (i + 2 < bytes.length) {
                        byte b1 = bytes[i + 1];
                        byte b2 = bytes[i + 2];
                        if (isAlphaByte(b1) && isAlphaByte(b2)) {
                            final RawPacket packet = new RawPacket(new byte[]{ current, b1, b2 }, charset);
                            out.add(packet);
                            i += 3;
                            continue;
                        }
                    }

                    // Fallback: DC2 + single letter
                    if (i + 1 < bytes.length) {
                        byte next = bytes[i + 1];
                        final RawPacket packet = new RawPacket(new byte[]{ current, next }, charset);
                        out.add(packet);
                        i += 2;
                    } else {
                        i++;
                    }

                    continue;
                }

                // DC1 or DLE followed by a letter (two-byte commands)
                if ((current == ControlCmd.DC1 || current == ControlCmd.DLE) && i + 1 < bytes.length) {
                    byte next = bytes[i + 1];
                    final RawPacket packet = new RawPacket(new byte[]{ current, next }, charset);
                    out.add(packet);
                    i += 2;
                    continue;
                }

                // Single-byte DLE representation (if needed)
                if (current == ControlCmd.DLE) {
                    final RawPacket packet = new RawPacket(current, charset);
                    out.add(packet);
                    i++;
                    continue;
                }

                // Other characters (could be part of print data) - skip for mock
                i++;
            }
        }

        private boolean isAlphaByte(byte b) {
            return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z');
        }

        /**
         * Handle individual command and send appropriate response
         */
        private void handleCommand(final RawPacket payload) throws IOException {
            final RawPacket response = onMessage(payload);
            if (response != null) {
                send(response);
            }
        }

        /**
         * Send raw bytes to the client
         */
        protected void send(final RawPacket payload) throws IOException {
            final byte[] bytes = payload.getBytes();
            out.write(bytes);
            out.flush();
            logger.debugf("Sent: '%s'", payload);
        }

    }

}
