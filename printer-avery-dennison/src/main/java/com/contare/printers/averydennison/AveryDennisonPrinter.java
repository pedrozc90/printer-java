package com.contare.printers.averydennison;

import com.contare.printers.core.BasePrinter;
import com.contare.printers.core.PrinterException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class AveryDennisonPrinter extends BasePrinter {

    private static final Pattern EPC_PATTERN = Pattern.compile("\\^FD[a-zA-Z0-9]{24}\\^FS");
    private static final Pattern EPC_CAPTURE_PATTERN = Pattern.compile("\\^FD([a-zA-Z0-9]{24})\\^FS");

    public AveryDennisonPrinter(final String ip, final Integer port) {
        super(ip, port);
    }

    @Override
    protected String createLabel(final String ip, final Integer port) {
        return String.format("avery-dennison@%s:%d", ip, port);
    }

    @Override
    public Set<String> send(final String content, final String sku, final Integer epcs) throws PrinterException {
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
                                onReceiveEpc(read);
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
    public void onReceiveEpc(final String epc) {
        logger.debugf("Received EPC: %s", epc);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing printer.");
    }

    @Override
    public void play() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

    @Override
    public void pause() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

    @Override
    public void cancel() throws PrinterException {
        throw new UnsupportedOperationException("Method Not Implemented.");
    }

}
