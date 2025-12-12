package com.contare.printers;

import com.contare.printers.core.Printer;
import com.contare.printers.core.PrinterException;
import com.contare.printers.mocks.SatoMock;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Set;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    private static final PrinterPool pool = PrinterPool.getInstance();

    private static final SatoMock sato = new SatoMock();

    static {
        try {
            sato.start();
        } catch (IOException e) {
            logger.error("Error starting mock printer", e);
        }
    }

    public static void main(final String[] args) {
        try {
            final String ip = sato.getHost();
            int port = sato.getPort();

            final Printer printer = pool.factory("SATO", ip, port);
            final Set<String> results = printer.send("xxx", "10101010", 0);
            logger.infof("Printed: %s", results);
        } catch (PrinterException e) {
            logger.error("Error while printing", e);
        }
    }

}
