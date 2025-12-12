package com.contare.printers.core;

import com.contare.printers.core.exceptions.PrinterException;
import com.contare.printers.core.objects.RawPacket;
import com.contare.printers.core.types.ParseFunction;
import com.contare.printers.core.utils.CmdUtils;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public abstract class BasePrinter implements Printer {

    protected final int MAX_ITERATIONS = 1_000_000; // to avoid infinite loops
    protected final int MAX_RECONNECTIONS = 5;      // maximum number of reconnection attempts
    protected final int READ_TIMEOUT = 15_500;      // maximum time with not response from socket

    protected final PrinterConnection connection;
    protected final Logger logger;

    protected final Set<String> _skus = ConcurrentHashMap.newKeySet();

    protected String sku;                   // last/current SKU printed
    protected boolean printing = false;
    protected boolean paused = false;

    public BasePrinter(final String ip, final Integer port) {
        connection = new PrinterConnection(ip, port);
        logger = Logger.getLogger(createLabel(ip, port));
    }

    protected abstract String createLabel(final String ip, final Integer port);

    @Override
    public void connect() throws PrinterException {
        try {
            connection.connect();
        } catch (IOException e) {
            throw new PrinterException(e, "Error connecting printer");
        }
    }

    @Override
    public void reconnect() throws PrinterException {
        try {
            connection.reconnect();
        } catch (IOException e) {
            throw new PrinterException(e, "Error reconnecting printer");
        }
    }

    @Override
    public void close() throws PrinterException {
        try {
            connection.close();
        } catch (IOException e) {
            throw new PrinterException(e, "Error closing printer connection");
        }
    }

    @Override
    public boolean cancelSku() throws PrinterException {
        final boolean canceled = cancel();
        if (canceled) {
            boolean ignored = ignoreSku(sku);
            if (ignored) {
                logger.infof("SKU %s canceled and ignored", sku);
            }
        }
        return canceled;
    }

    protected void setSku(final String sku) {
        this.sku = sku;
    }

    /**
     * Mark sku as ignored so on next 'print' calls
     *
     * @param sku - sku to be ignored.
     * @return true if successfully added to skus list.
     */
    protected boolean ignoreSku(final String sku) {
        return _skus.add(sku);
    }

    protected boolean isIgnoredSku(final String sku) {
        return _skus.contains(sku);
    }

    protected void clearIgnoredSkus() {
        _skus.clear();
    }

    // HELPERS

    /**
     * Send command and block until we receive an ACK/NAK or a framed response, or timeout.
     *
     * @param cmd       - printer command string.
     * @param timeout   - response timeout (milliseconds)
     * @param parser    - parse raw packets into vendor type messages.
     * @param predicate - stop predicate
     * @param <T>       - vendor-specific message type
     * @throws PrinterException if the command fails.
     */
    protected <T> List<T> sendCommandAndWait(final String cmd,
                                             final long timeout,
                                             final ParseFunction<RawPacket, List<T>> parser,
                                             final Predicate<List<T>> predicate) throws PrinterException {
        try {
            final List<T> out = new ArrayList<>();

            final String hex = CmdUtils.toHex(cmd, connection.getCharset());
            logger.debugf("Sending command: '%s'", hex);

            connection.send(cmd);

            final long start = System.currentTimeMillis();

            long elapsed = 0;
            while ((elapsed = System.currentTimeMillis() - start) < timeout) {
                final List<RawPacket> packets = connection.read();
                logger.debugf("Received %d packets from printer (%d ms)", packets.size(), elapsed);

                for (RawPacket row : packets) {
                    logger.debugf("Socket row: '%s'", row);

                    final List<T> messages = parser.apply(row);
                    logger.debugf("Socket parsed messages '%d'", messages.size());

                    out.addAll(messages);
                }

                final boolean done = (predicate != null) && predicate.test(out);
                if (done) {
                    return out;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PrinterException(e, "Interrupted while waiting for printer response.");
                }
            }

            return out;
        } catch (IOException e) {
            throw new PrinterException(e, "Error sending command to printer");
        }
    }

}
