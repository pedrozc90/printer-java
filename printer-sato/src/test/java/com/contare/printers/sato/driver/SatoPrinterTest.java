package com.contare.printers.sato.driver;

import com.contare.printers.sato.SatoPrinter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SatoPrinterTest {

    final SatoPrinter printer = new SatoPrinter("127.0.0.1", 9100);

    @Test
    public void test() {
        assertNotNull(printer);
    }

}
