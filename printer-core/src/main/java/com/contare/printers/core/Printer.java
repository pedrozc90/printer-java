package com.contare.printers.core;

import com.contare.printers.core.exceptions.PrinterException;

import java.util.Set;

public interface Printer extends AutoCloseable {

    /**
     * Send label content to printer
     *
     * @param content - label content
     * @param sku     - current sku
     * @param epcs    - number of epcs inside content
     * @return        - list of epcs printed by printer
     * @throws PrinterException
     */
    Set<String> print(final String content, final String sku, final Integer epcs) throws PrinterException;

    /**
     * Hook called when the printer returns a new EPC/TID.
     *
     * @param epc - rfid hexadecimal string.
     */
    void onReceiveEpc(final String epc, final String tid);

    /**
     * Initialize printer driver.
     */
    void initialize();

    /**
     * Connect to printer
     *
     * @throws PrinterException if unable to communicate with the printer.
     */
    void connect() throws PrinterException;

    /**
     * Disconnect from printer
     *
     * @throws PrinterException if unable to communicate with the printer.
     */
    void reconnect() throws PrinterException;

    /**
     * Resume the printing process.
     *
     * @return true if printer ACKed the command, false otherwise.
     * @throws PrinterException if the command fails.
     */
    boolean resume() throws PrinterException;

    /**
     * Pause the printing process.
     *
     * @throws PrinterException if the command fails.
     */
    boolean pause() throws PrinterException;

    /**
     * Cancel the current printing process.
     *
     * @return true if printer ACKed the command, false otherwise.
     * @throws PrinterException if the command fails.
     */
    boolean cancel() throws PrinterException;

    /**
     * Cancel the current printing SKU and its next files.
     *
     * obs.: If the number of epcs is too big, we need to partition the SBLP into multiple files.
     *       So if we are currently printing a tag of SKU X, we need to cancel all the next files of that same SKU.
     *
     * @return true if printer ACKed the command, false otherwise.
     * @throws PrinterException if the command fails.
     */
    boolean cancelSku() throws PrinterException;

}
