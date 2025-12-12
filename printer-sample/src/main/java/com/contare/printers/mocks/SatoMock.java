package com.contare.printers.mocks;

import org.jboss.logging.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SatoMock - lightweight TCP server that simulates a SATO printer for unit tests or manual testing.
 * <p>
 * Features:
 * - Starts a background server socket (choose port 0 for ephemeral port).
 * - Accepts multiple client connections concurrently.
 * - Recognizes the following command sequences (raw or framed with STX/ETX):
 * DC2+PH  -> 0x12 'P' 'H'   (Cancel Request)     -> responds with ACK (0x06) on success, or NAK (0x15)
 * DC2+PK  -> 0x12 'P' 'K'   (EPC/TID Request)    -> responds with framed payload STX payload CRLF ETX or NAK
 * DC2+PG  -> 0x12 'P' 'G'   (Printer Status Req) -> responds with framed payload STX payload CRLF ETX or NAK
 * - Accepts either unframed commands (just the bytes) or commands wrapped in STX (0x02) .. ETX (0x03).
 * - Default behavior: successful replies (ACK or framed payload). You can configure behavior with setters.
 * <p>
 * Usage example:
 * SatoMock mock = new SatoMock(0); // ephemeral port
 * mock.start();
 * int port = mock.getPort();
 * // connect in test to localhost:port and send bytes like b"\x12PH" or b"\x02\x12PH\x03"
 * // then read responses
 * mock.stop();
 * <p>
 * This class is intentionally small and dependency-free so it can be used from JUnit tests or main methods.
 */
public class SatoMock implements Closeable {

    private static final Logger logger = Logger.getLogger(SatoMock.class);

    // Control bytes
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte DC2 = 0x12;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;

    private ServerSocket server;
    private final ExecutorService clients;
    private Thread thread;

    // Behavior toggles (simple)
    private volatile boolean respondPkSuccess = true;
    private volatile boolean respondPgSuccess = true;
    private volatile boolean respondPhSuccess = true;

    // Simulated payloads (simple examples)
    private volatile String pkPayload = "25,1,N,ID:E200680612345678";
    private volatile String pgPayload = "32,PS0,RS0,RE0,PE0,EN00,BT0,Q000000";

    /**
     * Create SatoMock that listens on the requestedPort (0 for ephemeral).
     */
    public SatoMock() {
        this.clients = Executors.newCachedThreadPool();
    }

    /**
     * Start the mock server. This will bind the ServerSocket and start a background accept thread.
     *
     * @throws IOException if socket cannot be bound
     */
    public synchronized void start() throws IOException {
        if (server != null && !server.isClosed()) return;
        // create socket server
        server = new ServerSocket(0);
        server.setReuseAddress(true);

        final Acceptor acceptor = new Acceptor(server);

        thread = new Thread(acceptor, "acceptor");
        thread.setDaemon(true);
        thread.start();

        logger.debugf("Listening on port " + getPort());
    }

    /**
     * Stop the mock server and close all resources.
     *
     * @throws IOException on socket error
     */
    public synchronized void stop() throws IOException {
        if (server != null && !server.isClosed()) {
            try {
                server.close();
            } finally {
                server = null;
            }
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

        logger.debugf("Stopped");
    }

    @Override
    public void close() throws IOException {
        stop();
    }

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

    // --- Behavior setters for tests ------------------------------------------------

    /**
     * If false, the server will reply NAK to PK commands (useful to test error handling).
     */
    public void setRespondPkSuccess(boolean respondPkSuccess) {
        this.respondPkSuccess = respondPkSuccess;
    }

    /**
     * If false, the server will reply NAK to PG commands.
     */
    public void setRespondPgSuccess(boolean respondPgSuccess) {
        this.respondPgSuccess = respondPgSuccess;
    }

    /**
     * If false, the server will reply NAK to PH (cancel) commands.
     */
    public void setRespondPhSuccess(boolean respondPhSuccess) {
        this.respondPhSuccess = respondPhSuccess;
    }

    public void setPkPayload(String pkPayload) {
        this.pkPayload = pkPayload;
    }

    public void setPgPayload(String pgPayload) {
        this.pgPayload = pgPayload;
    }

    // --- Acceptor and client handling ---------------------------------------------

    private class Acceptor implements Runnable {

        private final ServerSocket _server;

        Acceptor(final ServerSocket s) {
            this._server = s;
        }

        @Override
        public void run() {
            try {
                while (!_server.isClosed()) {
                    final Socket client = _server.accept();
                    logger.debugf("Accepted client %s", client.getRemoteSocketAddress());
                    clients.execute(new ClientHandler(client));
                }
            } catch (IOException e) {
                if (_server.isClosed()) {
                    // normal shutdown
                } else {
                    logger.error("Accept error", e);
                }
            }
        }
    }

    private class ClientHandler implements Runnable {

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        // a little buffer to accumulate bytes across read() calls
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.socket.setSoTimeout(0); // blocking
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        @Override
        public void run() {
            try {
                byte[] tmp = new byte[512];
                int read;
                // read loop - when socket closes, read() returns -1 and we exit
                while ((read = in.read(tmp)) != -1) {
                    if (read > 0) {
                        buf.write(tmp, 0, read);
                        processBuffer();
                    }
                }
            } catch (IOException e) {
                // connection closed or error
                // log for debugging
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
         * Look for either framed (STX..ETX) regions or raw DC2 commands in the accumulated buffer.
         * Process all complete commands found and leave partial data for next read.
         */
        private void processBuffer() throws IOException {
            byte[] data = buf.toByteArray();
            int idx = 0;
            int consumed = 0;

            while (idx < data.length) {
                int b = data[idx] & 0xFF;
                if (b == STX) {
                    // try to find ETX
                    int etxIndex = -1;
                    for (int j = idx + 1; j < data.length; j++) {
                        if ((data[j] & 0xFF) == ETX) {
                            etxIndex = j;
                            break;
                        }
                    }
                    if (etxIndex == -1) {
                        // incomplete framed message; keep it for later
                        break;
                    }
                    // extract content between STX and ETX (not including STX/ETX)
                    byte[] content = new byte[etxIndex - (idx + 1)];
                    System.arraycopy(data, idx + 1, content, 0, content.length);
                    handleContent(content);
                    // consume through ETX
                    consumed = etxIndex + 1;
                    idx = consumed;
                } else if (b == DC2) {
                    // attempt to read 3-byte command DC2 + 'P' + X
                    if (idx + 2 >= data.length) {
                        // incomplete, wait for more
                        break;
                    }
                    byte p = data[idx + 1];
                    byte x = data[idx + 2];
                    // check pattern 0x12 'P' ('H'|'K'|'G')
                    if (p == 'P' && (x == 'H' || x == 'K' || x == 'G')) {
                        byte[] cmd = new byte[]{ DC2, p, x };
                        handleCommand(cmd);
                        consumed = idx + 3;
                        idx = consumed;
                    } else {
                        // unknown DC2 sequence - consume DC2 and continue
                        logger.debugf("Unknown DC2 sequence, dropping DC2 at buffer index '%d'", idx);
                        consumed = idx + 1;
                        idx = consumed;
                    }
                } else {
                    // could be single-byte unframed command like ACK/NAK from client? Not expected;
                    // try to see if it's ASCII characters representing a command (unlikely).
                    // To avoid infinite loop, we consume this byte.
                    // For real-world use you might want to ignore or log these bytes.
                    consumed = idx + 1;
                    idx = consumed;
                }
            }

            // remove consumed bytes from buffer: rebuild with remaining tail
            if (consumed > 0) {
                byte[] tail = new byte[data.length - consumed];
                System.arraycopy(data, consumed, tail, 0, tail.length);
                buf.reset();
                buf.write(tail, 0, tail.length);
            }
        }

        /**
         * The client sent a DC2+P? raw command (unframed).
         */
        private void handleCommand(byte[] cmd) throws IOException {
            // cmd is exactly 3 bytes: {0x12, 'P', <'H'|'K'|'G'>}
            byte x = cmd[2];
            if (x == 'H') {
                // Cancel request -> respond ACK or NAK (unframed)
                if (respondPhSuccess) {
                    sendUnframed(new byte[]{ ACK });
                    logger.debug("Received DC2+PH -> ACK");
                } else {
                    sendUnframed(new byte[]{ NAK });
                    logger.debug("Received DC2+PH -> NAK");
                }
            } else if (x == 'K') {
                // EPC/TID Return Request -> respond with framed payload or NAK
                if (respondPkSuccess) {
                    sendFramed(pkPayload);
                    logger.debug("Received DC2+PK -> framed PK payload sent");
                } else {
                    sendUnframed(new byte[]{ NAK });
                    logger.debug("Received DC2+PK -> NAK");
                }
            } else if (x == 'G') {
                // Printer status request -> framed payload or NAK
                if (respondPgSuccess) {
                    sendFramed(pgPayload);
                    logger.debug("Received DC2+PG -> framed PG payload sent");
                } else {
                    sendUnframed(new byte[]{ NAK });
                    logger.debug("Received DC2+PG -> NAK");
                }
            } else {
                // unexpected - send NAK
                sendUnframed(new byte[]{ NAK });
                logger.debug("Received unknown DC2+P? -> NAK");
            }
        }

        /**
         * Handle content from a framed message (bytes that were between STX and ETX).
         * The framed content might itself start with DC2 and a command (e.g., DC2+PH).
         */
        private void handleContent(byte[] content) throws IOException {
            // Strip trailing CR/LF if present (common in SATO responses)
            int len = content.length;
            if (len >= 2 && content[len - 2] == '\r' && content[len - 1] == '\n') {
                len -= 2;
            } else if (len >= 1 && (content[len - 1] == '\n' || content[len - 1] == '\r')) {
                len -= 1;
            }
            if (len <= 0) return;
            // Search for DC2 inside content and process commands found
            for (int i = 0; i < len; i++) {
                if ((content[i] & 0xFF) == DC2) {
                    if (i + 2 < len) {
                        byte p = content[i + 1];
                        byte x = content[i + 2];
                        if (p == 'P' && (x == 'H' || x == 'K' || x == 'G')) {
                            handleCommand(new byte[]{ DC2, p, x });
                            // continue scanning after this command
                            i += 2;
                            continue;
                        }
                    }
                }
            }
            // If no DC2 command found, ignore framed content (could be other commands we don't mock)
        }

        private void sendUnframed(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        private void sendFramed(String asciiPayload) throws IOException {
            // framed payload format: STX + asciiPayload + CRLF + ETX
            byte[] payloadBytes = asciiPayload.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            bout.write(STX);
            bout.write(payloadBytes);
            bout.write('\r');
            bout.write('\n');
            bout.write(ETX);
            byte[] framed = bout.toByteArray();
            out.write(framed);
            out.flush();
        }
    }

}
