package com.contare.printers.core;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BasePrinter implements Printer {

    protected final int MAX_ITERATIONS = 1_000_000; // to avoid infinite loops
    protected final int MAX_RECONNECTIONS = 5;      // maximum number of reconnection attempts
    protected final int READ_TIMEOUT = 15_500;      // maximum time with not response from socket

    protected final PrinterConnection connection;
    protected final Logger logger;

    protected final Set<String> abortedSkus = ConcurrentHashMap.newKeySet();

    protected String currentSku;
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

}
