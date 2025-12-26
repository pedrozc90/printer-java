package com.contare.printers.sample.mocks;

import com.contare.printers.core.objects.ControlCmd;
import com.contare.printers.core.objects.RawPacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.contare.printers.core.objects.ControlCmd.*;

public class SatoMock extends BaseMock {

    // responses
    private static final String PK_PAYLOAD = "%d,1,N,EP:%s,ID:%s\r\n";
    private static final String PG_PAYLOAD = "32,PS%s,RS0,RE0,PE0,EN00,BT0,Q%06d";
    private static final String IP0 = "\u001BIP0";

    private final Deque<String> buffer = new ArrayDeque<>(64);
    private boolean printing = false;
    private boolean paused = false;
    private int status = 0; // 0 = standby, 1 = waiting, 2 = analyzing, 3 = printing, 4 = offline, 5 = error

    public SatoMock() {
        super(StandardCharsets.UTF_8);
    }

    @Override
    public List<RawPacket> parseData(final byte[] data, final Charset charset) {
        final List<RawPacket> out = new ArrayList<>();

        int i = 0;
        while (i < data.length) {
            // Framed chunk starting with STX -> return a RawPacket containing the full frame (including STX/ETX)
            if (data[i] == STX) {
                int etxIndex = -1;
                for (int j = i + 1; j < data.length; j++) {
                    if (data[j] == ETX) {
                        etxIndex = j;
                        break;
                    }
                }

                if (etxIndex != -1) {
                    final byte[] framed = Arrays.copyOfRange(data, i + 1, etxIndex);

                    // Try to extract control commands from the framed payload (e.g. DC2+PG DC2+PK)
                    final List<RawPacket> cmds = extractCommandsFromBytes(framed);
                    if (!cmds.isEmpty()) {
                        out.addAll(cmds);
                    }

                    // Also include the full framed payload when it contains ESC or printable content
                    boolean hasESC = false;
                    boolean hasPrintable = false;
                    for (byte b : framed) {
                        if (b == 0x1B) { // ESC
                            hasESC = true;
                            break;
                        }
                        if (b > 0x1F && b != '\r' && b != '\n' && b != '\t') {
                            hasPrintable = true;
                            break;
                        }
                    }

                    if (hasESC || hasPrintable) {
                        out.add(new RawPacket(framed, charset));
                    }

                    i = etxIndex + 1;
                    continue;
                } else {
                    // No ETX found: take the rest as a (partial) framed packet
                    final byte[] rest = Arrays.copyOfRange(data, i, data.length);
                    out.add(new RawPacket(rest, charset));
                    break;
                }
            }

            // Non-framed data: find next STX and return the slice between
            int nextSTX = -1;
            for (int j = i; j < data.length; j++) {
                if (data[j] == STX) {
                    nextSTX = j;
                    break;
                }
            }

            final int endIndex = nextSTX == -1 ? data.length : nextSTX;
            if (endIndex > i) {
                final byte[] content = Arrays.copyOfRange(data, i, endIndex);

                // First try to extract discrete control commands (DC1/DC2/DLE etc.)
                final List<RawPacket> cmds = extractCommandsFromBytes(content);
                if (!cmds.isEmpty()) {
                    out.addAll(cmds);
                }

                // If the slice contains ESC sequences or printable characters
                // (not just CR/LF), include the whole slice so label bodies
                // remain available to onAny(). This avoids adding CR/LF-only packets.
                boolean hasESC = false;
                boolean hasPrintable = false;
                for (byte b : content) {
                    if (b == 0x1B) { // ESC
                        hasESC = true;
                        break;
                    }
                    if (b > 0x1F && b != '\r' && b != '\n' && b != '\t') {
                        hasPrintable = true;
                        break;
                    }
                }

                if (hasESC || hasPrintable) {
                    out.add(new RawPacket(content, charset));
                }
            }

            if (nextSTX == -1) {
                break;
            }
            i = nextSTX;
        }

        return out;
    }

    /**
     * Extract individual commands from bytes (handles multi-byte commands like DC2+PG)
     *
     * @return
     */
    private List<RawPacket> extractCommandsFromBytes(final byte[] bytes) {
        final List<RawPacket> out = new ArrayList<>();

        int i = 0;
        while (i < bytes.length) {
            byte current = bytes[i];

            // Handle DC2 (0x12) commands. Prefer DC2 + two-letter commands (e.g. DC2 + 'P' 'H').
            if (current == DC2) {
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
            if ((current == DC1 || current == DLE) && i + 1 < bytes.length) {
                byte next = bytes[i + 1];
                final RawPacket packet = new RawPacket(new byte[]{ current, next }, charset);
                out.add(packet);
                i += 2;
                continue;
            }

            // Single-byte DLE representation (if needed)
            if (current == DLE) {
                final RawPacket packet = new RawPacket(current, charset);
                out.add(packet);
                i++;
                continue;
            }

            // Other characters (could be part of print data) - skip for mock
            i++;
        }

        return out;
    }

    private boolean isAlphaByte(final byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z');
    }

    @Override
    public RawPacket handlePacket(final RawPacket packet) throws IOException {
        final String cmd = packet.toText();
        switch (cmd) {
            // DC2 + PG (Get printer status)
            case "\u0012PG":
                return onPG();
            // DC2 + PK (Get printer information)
            case "\u0012PK":
                return onPK();
            // DC2 + PH: Cancel
            case "\u0012PH":
                return onCancel();
            // DLE + H: Pause
            case "\u0010H":
                return onPause();
            // DC1 + H: Resume
            case "\u0011H":
                return onResume();
            default:
                return onAny(packet);
        }
    }

    private RawPacket onPG() throws IOException {
        logger.debugf("Handler: <DC2 + PG> - Get printer status");
        final int ps = status;
        final int remaining = buffer.size();
        final String value = String.format(PG_PAYLOAD, ps, remaining);
        return createFramedPacket(value);
    }

    private RawPacket onPK() throws IOException {
        logger.infof("Handler: <DC2 + PK> - Get EPC/TID");

        if (buffer.isEmpty()) {
            return null;
        }

        final String epc = buffer.pop();
        final String tid = epc.replaceAll("\\w", "0");
        final int bytes = epc.length() + tid.length() + 13;
        // [STX]25,1,N,ID:E200680612345678[CR][LF][ETX]
        // [STX]53,1,N,EP:E0123456789ABCDEF0123456,ID:E200680612345678[CR][LF][ETX]
        // [STX]x,1,N,EP:y,ID:zrn[ETX]
        final String value = String.format(PK_PAYLOAD, bytes, epc, tid);
        return createFramedPacket(value);
    }

    private RawPacket onCancel() {
        logger.debugf("Handler: <CD2 + PH> - Cancel printing");

        printing = false;
        paused = false;
        buffer.clear();

        return createPacket(ACK);
    }

    private RawPacket onPause() {
        logger.infof("Handler: <DLE + H> - Pause printing");

        if (paused) {
            return createPacket(NAK);
        }

        paused = true;
        return createPacket(ACK);
    }

    private RawPacket onResume() {
        logger.infof("Handler: <DC1 + H> - Resume printing");

        if (!paused) {
            return createPacket(NAK);
        }

        paused = false;
        return createPacket(ACK);
    }

    // extract epc from "epc:<value>,fsw:0"
    final Pattern pattern = Pattern.compile("epc:([^,;]+)");


    private RawPacket onAny(final RawPacket payload) {
        final String text = payload.toText();

        // check if contains label body
        if (text.contains(IP0)) {
            // collect epcs
            final Matcher matcher = pattern.matcher(text);
            int n = 1;
            while (matcher.find()) {
                final String epc = matcher.group(n++);
                buffer.add(epc);
                printing = true;
                paused = false;
            }
        } else {
            logger.warnf("Unknown command: %s", payload);
        }
        return null;
    }

    // HELPERS
    private RawPacket createPacket(final byte[] bytes) {
        return new RawPacket(bytes, charset);
    }

    private RawPacket createPacket(final byte b) {
        return new RawPacket(new byte[]{ b }, charset);
    }

    private RawPacket createFramedPacket(final byte[] bytes) throws IOException {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(STX); // STX
        bout.write(bytes);
        bout.write(ETX); // ETX

        final byte[] framed = bout.toByteArray();

        return createPacket(framed);
    }

    private RawPacket createFramedPacket(final String value) throws IOException {
        final byte[] bytes = value.getBytes(charset);
        return createFramedPacket(bytes);
    }

}
