package com.contare.printers.zebra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ZebraPrinterTest {

    final ZebraPrinter printer = new ZebraPrinter("127.0.0.1", 9100);

    @Test
    public void test() {
        assertNotNull(printer);
    }

}
