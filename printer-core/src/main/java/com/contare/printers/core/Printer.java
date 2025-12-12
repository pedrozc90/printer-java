package com.contare.printers.core;

import java.util.Set;

public interface Printer extends AutoCloseable {

    /**
     * Send label content to printer
     *
     * @param content - label content
     * @param sku     - current sku
     * @param epcs    - number of epcs inside content
     * @return - list of epcs printed by printer
     * @throws PrinterException
     */
    Set<String> send(final String content, final String sku, final Integer epcs) throws PrinterException;

    void onReceiveEpc(final String epc);

    /**
     * Initialize printer driver
     */
    void initialize();

    void connect() throws PrinterException;

    void reconnect() throws PrinterException;

    /**
     * Send command to printer start printing
     *
     * @throws PrinterException
     */
    void play() throws PrinterException;

    /**
     * Send command to printer pause printing
     *
     * @throws PrinterException
     */
    void pause() throws PrinterException;

    /**
     * Send command to cancel printing
     *
     * @throws PrinterException
     */
    void cancel() throws PrinterException;

}
