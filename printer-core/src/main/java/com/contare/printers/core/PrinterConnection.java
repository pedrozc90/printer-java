package com.contare.printers.core;

import com.contare.printers.core.objects.RawPacket;
import lombok.Getter;
import org.jboss.logging.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class PrinterConnection implements Closeable {

    // control characters
    private static final int EOF = -1;      // end of file.
    private static final int STX = 0x02;    // start of text: first character of message text, and may be used to terminate the message heading.
    private static final int ETX = 0x03;    // end of text: in message transmission, delimits the end of the main text of a message.

    private final Logger logger = Logger.getLogger(PrinterConnection.class);

    private final String ip;
    private final Integer port;
    private final Charset charset;

    private final Object lock = new Object();
    private Socket _socket;
    private BufferedInputStream _input;
    private BufferedOutputStream _output;

    public PrinterConnection(final String ip, final Integer port, final Charset charset) {
        this.ip = Objects.requireNonNull(ip, "IP address is required");
        this.port = Objects.requireNonNull(port, "Port is required");
        this.charset = (charset != null) ? charset : StandardCharsets.UTF_8;
    }

    public PrinterConnection(final String ip, final Integer port) {
        this(ip, port, StandardCharsets.UTF_8);
    }

    public boolean isConnected() {
        return (_socket != null && _socket.isConnected() && !_socket.isClosed());
    }

    public void connect(final int timeout) throws IOException {
        synchronized (lock) {
            try {
                // create tcp socket
                _socket = new Socket();
                _socket.connect(new InetSocketAddress(ip, port), timeout);
                _socket.setSoTimeout(5_000);
                _socket.setTcpNoDelay(true);

                // create buffered streams for efficient IO
                _input = new BufferedInputStream(_socket.getInputStream());
                _output = new BufferedOutputStream(_socket.getOutputStream());

                logger.debugf("Connected to printer %s:%d (timeout = %d ms)", ip, port, timeout);
            } catch (SocketTimeoutException e) {
                logger.errorf(e, "Socket timeout connecting to printer %s:%d", ip, port);
                throw e;
            } catch (SocketException e) {
                logger.errorf(e, "Socket error on printer %s:%d", ip, port);
                throw e;
            } catch (IOException e) {
                logger.errorf(e, "Error connecting to printer %s:%d", ip, port);
                throw e;
            }
        }
    }

    public void connect() throws IOException {
        connect(5_000);
    }

    private void disconnect() {
        try {
            if (_output != null) {
                try {
                    _output.flush();
                } catch (IOException e) {
                    logger.errorf(e, "Error flushing output stream");
                }
                try {
                    _output.close();
                } catch (IOException e) {
                    logger.errorf(e, "Error closing output stream");
                }
            }

            if (_input != null) {
                try {
                    _input.close();
                } catch (IOException e) {
                    logger.errorf(e, "Error closing input stream");
                }
            }

            if (_socket != null && !_socket.isClosed()) {
                try {
                    _socket.close();
                } catch (IOException e) {
                    logger.errorf(e, "Error closing socket");
                }
            }
        } finally {
            _output = null;
            _input = null;
            _socket = null;
        }
    }

    public void reconnect() throws IOException {
        if (isConnected()) {
            disconnect();
        }
        connect();
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            disconnect();
        }
    }

    public String status() {
        if (_socket == null) {
            return "NOT FOUND";
        } else if (_socket.isClosed()) {
            return "CLOSED";
        } else if (_socket.isConnected()) {
            return "CONNECTED";
        } else if (_socket.isBound()) {
            return "BOUND";
        }
        return "DISCONNECTED";
    }

    /**
     * Send a String to the printer using the configured charset. This writes raw bytes and flushes.
     *
     * @param data payload
     * @throws IOException if IO error or not connected
     */
    public void send(final String data) throws IOException {
        if (data == null) return;
        final byte[] bytes = data.getBytes(charset);
        send(bytes);
    }

    /**
     * Send raw bytes to the printer and flush.
     *
     * @param bytes payload
     * @throws IOException if IO error or not connected
     */
    public void send(final byte[] bytes) throws IOException {
        if (bytes == null) return;
        synchronized (lock) {
            try {
                _output.write(bytes);
                _output.flush();
            } catch (IOException e) {
                // TODO: Should I implement a custom exception for printer connection errors?
                throw e;
            }
        }
    }

    /**
     * Read all lines from the printer until EOF.
     *
     * @return List of string payload, message start with STX and end with ETX.
     * @throws IOException if IO error
     */
    public List<String> readAsString() throws IOException {
        final List<String> results = new ArrayList<>();

        synchronized (lock) {
            if (_input == null) {
                throw new IOException("Not connected to printer (input stream is null)");
            }

            ByteArrayOutputStream current = null;
            int b;

            try {
                while (true) {
                    try {
                        b = _input.read();
                        logger.debugf("Received: '%d' ('%c')", b, (char) b);
                    } catch (SocketTimeoutException e) {
                        // No more data available right now; return what we have collected.
                        logger.debugf("Socket read timed out while reading from printer %s:%d; returning %d complete message(s)", ip, port, results.size());
                        break;
                    }

                    if (b == EOF) {
                        logger.debugf("EOF reached on printer connection %s:%d", ip, port);
                        break;
                    } else if (b == STX) {
                        // start new message (include STX)
                        current = new ByteArrayOutputStream();
                        current.write(b);
                    } else if (current != null) {
                        // we are inside a message; add the byte
                        current.write(b);
                        if (b == ETX) {
                            // message complete; convert and add to results
                            final String message = new String(current.toByteArray(), charset);
                            results.add(message);
                            current = null;
                        }
                    } else {
                        // byte arrived outside of a message start; ignore but log at debug
                        logger.debugf("Ignoring byte outside STX..ETX: '%d' ('%c')", b, (char) b);
                    }
                }
            } catch (IOException e) {
                // an IO error occurred during read; wrap or rethrow
                logger.errorf(e, "IO error reading from printer %s:%d", ip, port);
                throw e;
            }

            // If socket closed or EOF reached with a partial message in 'current', we drop the incomplete message.
            if (current != null) {
                logger.debugf("Dropping incomplete message (STX seen but no ETX) from printer %s:%d", ip, port);
            }
        }

        return results;
    }

    /**
     * Read available bytes from the InputStream until a read() times out or EOF.
     * Each read() chunk is returned as a RawPacket (one array per successful read()).
     * Caller must configure socket/serial read timeout so this method returns reasonably.
     */
    public List<RawPacket> read() throws IOException {
        final List<RawPacket> out = new ArrayList<>();
        byte[] buf = new byte[4096];

        while (true) {
            int n;
            try {
                n = _input.read(buf); // blocks until data or timeout/EOF
            } catch (SocketTimeoutException ste) {
                // no more data available at the moment
                break;
            }

            if (n == -1) {
                // EOF
                break;
            } else if (n == 0) {
                // nothing read; loop or break depending on stream behavior
                break;
            } else {
                byte[] copy = new byte[n];
                System.arraycopy(buf, 0, copy, 0, n);
                out.add(new RawPacket(copy, charset));
                // continue reading until read() times out
            }
        }

        return out;
    }

}
