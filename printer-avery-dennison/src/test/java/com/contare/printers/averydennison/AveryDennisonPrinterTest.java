package com.contare.printers.averydennison;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AveryDennisonPrinterTest {

    final AveryDennisonPrinter printer = new AveryDennisonPrinter("127.0.0.1", 9100);

    @Test
    public void test() {
        assertNotNull(printer);
    }

}
