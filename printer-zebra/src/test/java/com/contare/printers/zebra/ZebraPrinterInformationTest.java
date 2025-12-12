package com.contare.printers.zebra;

import com.contare.printers.zebra.objects.ZebraPrinterInformation;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ZebraPrinterInformationTest {

    private ResourceHelper helper = new ResourceHelper();

    @Test
    public void test() throws IOException {
        final String content = helper.getAsString("sample.txt");
        final ZebraPrinterInformation result = ZebraPrinterInformation.parse(content);
        assertNotNull(result);
    }

}
