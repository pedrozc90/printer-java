package com.contare.printers.averydennison;

import com.contare.printers.core.BasePrinter;
import com.contare.printers.core.exceptions.PrinterException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AveryDennisonPrinter extends BasePrinter {

    public AveryDennisonPrinter(final String ip, final Integer port) {
        super(ip, port);
    }

    @Override
    protected String createLabel(final String ip, final Integer port) {
        return String.format("avery-dennison@%s:%d", ip, port);
    }

    @Override
    public Set<String> print(final String content, final String sku, final Integer epcs) throws PrinterException {
        final Set<String> results = new TreeSet<>();

        int iteration = 0;                                  // loop iterations
        long elapsedTime = 0L;
        long lastReadTime = System.currentTimeMillis();
        long maxTimeWithoutRead = 15_000;

        try {
            connection.reconnect();

            connection.send(content);

            while ((elapsedTime = System.currentTimeMillis() - lastReadTime) < maxTimeWithoutRead) {
                logger.infof("Socket iteration: %d - elapsed time: %d ms", iteration, elapsedTime);

                try {
                    final List<String> payload = connection.readAsString();
                    logger.infof("Socket read %d lines", payload.size());

                    for (String read : payload) {
                        logger.infof("Socket read: %s", read);

                        if (StringUtils.isNotBlank(read)) {
                            lastReadTime = System.currentTimeMillis();
                            boolean added = results.add(read);
                            if (added) {
                                onReceiveEpc(read, null);
                            }
                        }
                    }

                    iteration++;
                } catch (IOException e) {
                    logger.error("Failed to read socket", e);
                }
            }
        } catch (IOException e) {
            throw new PrinterException(e);
        } finally {
            // stop printing
            printing = false;
            // close printer connection
            close();
        }

        return results;
    }

    @Override
    public void onReceiveEpc(final String epc, final String tid) {
        logger.debugf("Received EPC: '%s', TID: '%s'", epc ,tid);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing printer.");
    }

    @Override
    public boolean resume() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

    @Override
    public boolean pause() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

    @Override
    public boolean cancel() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

    @Override
    public boolean cancelSku() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

}
