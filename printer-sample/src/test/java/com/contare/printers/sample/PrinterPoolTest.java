package com.contare.printers.sample;

import com.contare.printers.core.Printer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrinterPoolTest {

    private final PrinterPool pool = PrinterPool.getInstance();

    @Test
    public void test() {
        final Map<PrinterPool.Pair<String, Integer>, Printer> map = pool.getPool();
        assertEquals(0, map.size());
    }

}
