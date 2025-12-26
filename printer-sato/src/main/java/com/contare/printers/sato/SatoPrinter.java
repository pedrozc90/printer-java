package com.contare.printers.sato;

import com.contare.printers.core.BasePrinter;
import com.contare.printers.core.exceptions.PrinterException;
import com.contare.printers.sato.enums.PrinterStatus;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class SatoPrinter extends BasePrinter {

    public SatoPrinter(final String ip, final Integer port) {
        super(ip, port);
    }

    @Override
    protected String createLabel(final String ip, final Integer port) {
        return String.format("Sato@%s:%d", ip, port);
    }

    @Override
    public Set<String> print(final String content, final String sku, final Integer epcs) throws PrinterException {
        Objects.requireNonNull(content, "SBPL content cannot be null");

        // list of results
        final Set<String> results = new TreeSet<>();

        // normalize label file content
        final String normalized = content.replaceAll("\n", "\r\n");

        setSku(sku);

        try {
            if (isIgnoredSku(sku)) {
                logger.warnf("Aborting printing of sku '%s'", sku);
                return results;
            }

            // make sure the printer is connected
            connection.reconnect();

            // clear printer buffer
            final boolean canceled = queryCancel();
            if (!canceled) {
                throw new PrinterException("Failed to cancel previous printing job.");
            }

            // TODO: do we really need it ?
            printing = true;

            logger.infof("Sku: '%s'", sku);
            logger.infof("Number of EPCs: '%d'", epcs);
            logger.info("------------------------------------------------------------");
            logger.info("# SBPL");
            logger.info("------------------------------------------------------------");
            logger.info(normalized);
            logger.info("------------------------------------------------------------");

            // send SBPL to printer
            connection.send(normalized);

            // wait a little since se send a big file to the printer
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                throw new PrinterException("Interrupted while waiting after sending SBPL");
            }

            SatoMessage.PrinterInfo prev = null;        // store the last print status message received

            int iteration = 0;                          // count the number of iterations
            int remaining = Integer.MAX_VALUE;          // remaining number of tags to print (equal to printer status Q parameter)
            long elapsed = 0;                           // elapsed time between iterations
            long start = System.currentTimeMillis();    // start time of iteration
            long minPs0Received = 8;                    // we consider finish when we receive 8 'standby' status in sequence
            long ps0Received = 0;                       // count the amount of 'standby' status returned in sequence

            final int MAX_COUNTER = 3;
            int stableCount = 0;

            _loop:
            while ((elapsed = System.currentTimeMillis() - start) < READ_TIMEOUT && remaining > 0) {
                logger.infof("Socket iteration '%d' (%d ms)", iteration, elapsed);

                // request printer status and EPC/TID
                final List<SatoMessage> messages = this.queryStatusAndTags();
                logger.debugf("Socket received '%d' messages", messages.size());

                // update the last-received timestamp so timeout is relative to the last activity
                start = System.currentTimeMillis();

                for (SatoMessage m : messages) {
                    // check printer status
                    if (m instanceof SatoMessage.PrinterInfo) {
                        final SatoMessage.PrinterInfo obj = (SatoMessage.PrinterInfo) m;
                        logger.infof("Printer status: %s", obj);

                        if (!Objects.equals(obj, prev)) {
                            remaining = obj.getQ();
                            onUpdateStatus(obj);
                        }

                        if (remaining == 0 && obj.getPs() == PrinterStatus.STANDBY) {
                            stableCount++;
                            logger.debugf("Printer status is STANDBY, count: %d", stableCount);
                            if (stableCount >= (MAX_COUNTER - 1)) {
                                printing = false;
                                break _loop;
                            }
                        } else {
                            stableCount = 0;
                        }

                        prev = obj;
                    }
                    // collect epc
                    else if (m instanceof SatoMessage.TagInfo) {
                        final SatoMessage.TagInfo obj = (SatoMessage.TagInfo) m;
                        logger.infof("Tag info: %s", obj);

                        final String epc = obj.getEpc();
                        final String tid = obj.getTid();
                        if (epc != null && results.add(epc)) {
                            onReceiveEpc(epc, tid);
                        }
                    }
                    // command failed
                    else if (m instanceof SatoMessage.Nak) {
                        logger.warn("Failed to query printer status and EPC");
                        // break _loop;
                    }
                    // unknown
                    else {
                        logger.warnf("Unexpected message type received: '%s'", m);
                    }
                }

                iteration++;
            }
        } catch (IOException e) {
            throw new PrinterException(e, "Error communicating with printer");
        } finally {
            printing = false;

            try {
                // cancel printing
                // if the loop broke because of an error, the printer will continue anyway, so let's try to force it to stop
                cancel();
            } catch (PrinterException e) {
                logger.error("Error cancelling printing job", e);
            }

            try {
                // close printer connection
                close();
            } catch (PrinterException e) {
                logger.error("Error closing printer connection", e);
            }
        }

        return results;
    }

    public void onUpdateStatus(final SatoMessage.PrinterInfo curr) {
        logger.debugf("Printer status changed to: %s", curr);
    }

    @Override
    public void onReceiveEpc(final String epc, final String tid) {
        logger.debugf("Received EPC: '%s', TID: '%s'", epc, tid);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing printer.");
        printing = false;
        // this.paused = false;
        // this.abort = false;
        // this.skus.clear();
    }

    // ACTIONS
    @Override
    public boolean resume() throws PrinterException {
        final boolean resumed = queryResume();
        if (resumed) {
            logger.info("Printing resumed.");
        } else {
            logger.warn("Failed to resume printing.");
        }
        return resumed;
    }

    @Override
    public boolean pause() throws PrinterException {
        final boolean paused = queryPause();
        if (paused) {
            logger.info("Printing paused.");
        } else {
            logger.warn("Failed to pause printing.");
        }
        return paused;
    }

    @Override
    public boolean cancel() throws PrinterException {
        final boolean cancelled = queryCancel();
        if (cancelled) {
            logger.info("Printing cancelled.");
        } else {
            logger.warn("Failed to cancel printing.");
        }
        return cancelled;
    }

    // COMMANDS

    /**
     * Command used to request printer status and tag EPC/TID
     *
     * @throws PrinterException
     */
    protected List<SatoMessage> queryStatusAndTags() throws PrinterException {
        // DC2 + PG = command returns the printer status. (requires PK command to return, pg. 435)
        // DC2 + PK = command returns the status of RFID tag write by <IP0> command and EPC/TID. (pg. 444, 451)
        final String cmd = "\u0002\u0012PG\u0012PK\u0003";
        return sendCommandAndWait(cmd, 1_000);
    }

    /**
     * <DC2 + PG>: Command used to request printer status information.
     * <p>
     * obs.:
     * 1. Return data format <[STX]a...a,b...bc,d...de,...[ETX]> (e.g: [STX]32,PS0,RS0,RE0,PE0,EN00,BT0,Q000000[ETX])
     * 2. Return data <NAK> when a command error occurs
     *
     * @throws PrinterException
     */
    protected SatoMessage.PrinterInfo queryPrinterStatus() throws PrinterException {
        // DC2 + PG = command returns the printer status. (requires PK command to return, pg. 435)
        final String cmd = "\u0002\u0012PG\u0003";
        final List<SatoMessage> messages = this.sendCommandAndWait(cmd, 1_000);
        for (SatoMessage m : messages) {
            if (m instanceof SatoMessage.PrinterInfo) {
                return (SatoMessage.PrinterInfo) m;
            }
        }
        return null;
    }

    /**
     * <DC2 + PK>: Command used to request status of RFID tag write by <IP0> command and EPC/TID
     * <p>
     * obs.:
     * 1. Return data format <[STX]a...a,b,c,d...d[ETX]> (e.g: EP:E0123456789ABCDEF0123456,ID:E200680612345678)
     * 2. Return data <NAK> when a command error occurs
     *
     * @throws PrinterException
     */
    protected SatoMessage.TagInfo queryEPCAndTID() throws PrinterException {
        // DC2 + PK = command returns the status of RFID tag write by <IP0> command and EPC/TID. (pg. 444, 451)
        final String cmd = "\u0002\u0012PK\u0003";
        final List<SatoMessage> messages = sendCommandAndWait(cmd, 1_000);
        for (SatoMessage m : messages) {
            if (m instanceof SatoMessage.TagInfo) {
                return (SatoMessage.TagInfo) m;
            }
        }
        return null;
    }

    /**
     * <DC1 + H>: Command used to resume printing.
     * <p>
     * obs.:
     * 1. Return <ACK> (HEX 06H) - No error in the printer
     * 2. Return <NAK> (HEX 15H) - Error in the printer
     *
     * @throws PrinterException
     */
    protected boolean queryResume() throws PrinterException {
        // DC1 + H = command starts printing. (pg. 495)
        final String cmd = "\u0002\u0011H\u0003";
        return sendControlCommand(cmd, 500);
    }

    /**
     * <DLE + H>: Command used to pause print printing.
     * <p>
     * obs.:
     * 1. Return <ACK> (HEX 06H) - No error in the printer
     * 2. Return <NAK> (HEX 15H) - Error in the printer
     *
     * @throws PrinterException
     */
    protected boolean queryPause() throws PrinterException {
        // DLE + H = command pause printing. (pg. 495)
        final String cmd = "\u0002\u0010H\u0003";
        return sendControlCommand(cmd, 500);
    }

    /**
     * <DC2 + PH>: Cancel used to cancel print jobs and clear printer buffer.
     * <p>
     * obs.:
     * 1. Return <ACK> - Ok
     * 2. Return <NAK> - Error
     *
     * @throws PrinterException -- when socket connection is lost.
     */
    protected boolean queryCancel() throws PrinterException {
        // DC2 + PH = This command cancels print jobs and clears the entire contents of receive buffer. (pg. 438)
        final String cmd = "\u0002\u0012PH\u0003";
        return this.sendControlCommand(cmd, 1_000);
    }

    /**
     * <DC2 + DC>: Command used to restart the printer.
     * <p>
     * obs.:
     * 1. Printer response with <NAK> (only during printing)
     *
     * @return
     * @throws PrinterException
     */
    protected boolean queryReset() throws PrinterException {
        // DC2 + DC = This command resets the printer to its default state. (pg. 400)
        final String cmd = "\u0002\u0012DC\u0003";
        return sendControlCommand(cmd, 500);
    }

    /**
     * <DC2 + DD>: Command used to turn off the printer.
     * <p>
     * obs.:
     * 1. Printer response with <NAK> (only during printing)
     *
     * @return
     * @throws PrinterException
     */
    protected boolean queryPowerOff() throws PrinterException {
        // DC2 + DD = This command powers off the printer. (pg. 400)
        final String cmd = "\u0002\u0012DD\u0003";
        return sendControlCommand(cmd, 500);
    }

    // HELPERS

    /**
     * Send command and block until we receive an ACK/NAK or a framed response, or timeout.
     *
     * @param cmd     - printer command string.
     * @param timeout - response timeout (milliseconds)
     * @return parsed SatoMessage list, it may be empty on timeout.
     * @throws PrinterException
     */
    protected List<SatoMessage> sendCommandAndWait(final String cmd, final long timeout) throws PrinterException {
        return this.sendCommandAndWait(
            cmd,
            timeout,
            (p) -> SatoParser.parse(p),
            (messages) -> messages.stream().anyMatch((m) ->
                m instanceof SatoMessage.Ack
                    || m instanceof SatoMessage.Nak
                    || m instanceof SatoMessage.PrinterInfo
                    || m instanceof SatoMessage.TagInfo
            )
        );
    }

    /**
     * Send control command and return true on ACK, false on NAK / timeout.
     *
     * @param cmd     -- printer command string.
     * @param timeout -- response timeout
     * @return
     * @throws PrinterException
     */
    protected boolean sendControlCommand(final String cmd, final long timeout) throws PrinterException {
        final List<SatoMessage> messages = sendCommandAndWait(cmd, timeout);
        for (SatoMessage m : messages) {
            if (m instanceof SatoMessage.Ack) {
                return true;
            } else if (m instanceof SatoMessage.Nak) {
                return false;
            }
        }
        return false;
    }

}
