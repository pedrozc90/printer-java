package com.contare.printers.core;

import lombok.Data;

import java.util.Arrays;

@Data
public final class RawPacket {

    private final byte[] data;
    private final long timestamp = System.currentTimeMillis();

    public RawPacket(final byte[] data) {
        this.data = (data != null) ? Arrays.copyOf(data, data.length) : new byte[0];
    }

    public int length() {
        return data.length;
    }

    public String toHex() {
        final StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("RawPacket[len = %d, ts = %d, hex = %s]", length(), timestamp, toHex());
    }

}
