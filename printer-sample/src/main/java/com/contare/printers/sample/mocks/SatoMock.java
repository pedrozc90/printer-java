package com.contare.printers.sample.mocks;

import com.contare.printers.core.objects.ControlCmd;
import com.contare.printers.core.objects.RawPacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SatoMock extends BaseMock {

    // responses
    private static final String PK_PAYLOAD = "53,1,N,EP:%s,ID:%s\r\n";
    private static final String PG_PAYLOAD = "32,PS0,RS0,RE0,PE0,EN00,BT0,Q000000";
    private static final String ACK = "\u0006";
    private static final String NAK = "\u0015";

    private boolean printing = false;
    private boolean paused = false;
    private Deque<String> buffer = new ArrayDeque<>(64);

    public SatoMock() {
        super(StandardCharsets.UTF_8);
    }

    @Override
    public RawPacket onMessage(final RawPacket packet) throws IOException {
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
        final String value = PG_PAYLOAD;
        return createFramedPacket(value);
    }

    private RawPacket onPK() throws IOException {
        logger.infof("Handler: <DC2 + PK> - Get EPC/TID");
        final String epc = "0";
        final String tid = "0";
        final String value = String.format(PK_PAYLOAD, epc, tid);
        return createFramedPacket(value);
    }

    private RawPacket onCancel() {
        logger.debugf("Handler: <CD2 + PH> - Cancel printing");

        printing = false;
        paused = false;
        buffer.clear();

        return createPacket(ControlCmd.ACK);
    }

    private RawPacket onPause() {
        logger.infof("Handler: <DLE + H> - Pause printing");

        if (!paused) {
            return createPacket(ControlCmd.NAK);
        }

        paused = true;
        return createPacket(ControlCmd.ACK);
    }

    private RawPacket onResume() {
        logger.infof("Handler: <DC1 + H> - Resume printing");

        if (!paused) {
            return createPacket(ControlCmd.NAK);
        }

        paused = false;
        return createPacket(ControlCmd.ACK);
    }

    // extract epc from "epc:<value>,fsw:0"
    final Pattern pattern = Pattern.compile("epc:([^,;]+)");

    private RawPacket onAny(final RawPacket payload) {
        logger.warnf("Unknown command: %s", payload);
        if (payload.isFramed()) {
            final String text = payload.toText();

            final String cmd = new String(new byte[]{ 0x1B, 'I', 'P', '0' }, charset);
            final boolean hasESCPI0 = text.contains(cmd);
            if (hasESCPI0) {
                final Matcher matcher = pattern.matcher(text);
                int n = 1;
                while (matcher.find()) {
                    final String epc = matcher.group(n++);
                    buffer.add(epc);
                }
            }
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
        bout.write((byte) 0x02); // STX
        bout.write(bytes);
        bout.write((byte) 0x03); // ETX

        final byte[] framed = bout.toByteArray();

        return createPacket(framed);
    }

    private RawPacket createFramedPacket(final String value) throws IOException {
        final byte[] bytes = value.getBytes(charset);
        return createFramedPacket(bytes);
    }

}
