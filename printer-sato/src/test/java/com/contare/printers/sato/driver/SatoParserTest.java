package com.contare.printers.sato.driver;

import com.contare.printers.core.RawPacket;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SatoParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
        // PH
        "\u0006",
        // "\u0002\u0006\u0003",
        "\u0015",
        // "\u0002\u0015\u0003",
        // PG
        "\u000232,PS2,RS0,RE0,PE0,EN00,BT0,Q000000\u0003",
        // PK
        "\u000253,1,N,EP:E0123456789ABCDEF0123456,ID:E200680612345678\r\n\u0003", // write successful (EPC read successful, TID read successful)
        "\u000225,1,N,ID:E200680612345678\r\n\u0003",                             // write successful (TID read successful)
        "\u00029,1,T,ID:\r\n\u0003",                                              // write successful (TID read fail)
        "\u00029,0,E,ID:\r\n\u0003",                                              // write fail (EPC write fail)
    })
    public void parser(final String value) throws IOException {
        final RawPacket packet = rawPacket(value);
        final List<SatoMessage> result = SatoParser.parse(packet);
        assertEquals(1, result.size());
    }

    private RawPacket rawPacket(final String data) {
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return new RawPacket(bytes);
    }

}
