package com.contare.printers.sample;

import com.contare.printers.core.Printer;
import com.contare.printers.sample.mocks.SatoMock;
import com.contare.printers.sample.utils.ResourceUtils;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    private static final PrinterPool pool = PrinterPool.getInstance();

    private static final ResourceUtils resources = ResourceUtils.getInstance();

    public static void main(final String[] args) {
        final SatoMock sato = new SatoMock();

        final String ip = sato.getHost();
        int port = sato.getPort();

        try (final Printer printer = pool.factory("SATO", ip, port)) {
            printer.initialize();

            try {
                final String sku = "812345";
                final int qtd = 1;
                final String content = resources.getAsString("files/SBPL.txt", StandardCharsets.UTF_8);

                final Set<String> results = printer.print(content, sku, qtd);
                logger.infof("Printed: %s", results);
            } catch (IOException e) {
                logger.error("Error while reading file", e);
            }
        } catch (Exception e) {
            logger.error("Error while printing", e);
        } finally {
            try {
                sato.close();
            } catch (IOException e) {
                logger.error("Error while closing mock", e);
            }
        }
    }

}
