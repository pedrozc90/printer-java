package com.contare.printers.sato.driver;

import com.contare.printers.core.BasePrinter;
import com.contare.printers.core.PrinterException;
import com.contare.printers.core.RawPacket;
import com.contare.printers.sato.enums.PrinterStatus;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class SatoPrinter extends BasePrinter {

    private static final Pattern EPC_PATTERN = Pattern.compile("epc:[0-9a-zA-Z]{24}");
    private static final Pattern EPC_CAPTURE_PATTERN = Pattern.compile("epc:([0-9a-zA-Z]{24})");

    public SatoPrinter(final String ip, final Integer port) {
        super(ip, port);
    }

    @Override
    protected String createLabel(final String ip, final Integer port) {
        return String.format("sato@%s:%d", ip, port);
    }

    @Override
    public Set<String> send(final String content, final String sku, final Integer epcs) throws PrinterException {
        Objects.requireNonNull(content, "content cannot be null");

        // list of results
        final Set<String> results = new TreeSet<>();

        // normalize label file content
        final String normalized = content.replaceAll("\n", "\r\n");

        currentSku = sku;

        try {
            // make sure the printer is connected
            connection.reconnect();

            // cancelar a impressao de todos os sku de uma impressão
            if (abortedSkus.contains(currentSku)) {
                logger.infof("Aborting printing -> sku: %s", sku);
                return results;
            }

            // clear printer buffer
            this.cancel();

            // make sure the printer is connected
            connection.reconnect();

            printing = true;                   // marcado como false quando a impressão é cancelada ou quando recebemos o comando de finalização.

            logger.infof("Send content -> sku: %s - number of epcs: %d", sku, epcs);
            logger.info("------------------------------------------------------------");
            logger.info(normalized);
            logger.info("------------------------------------------------------------");

            // send content to printer
            connection.send(normalized);

            // wait a little since se send a big file to the printer
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                // ignore
            }

            SatoMessage prev = null;

            int iteration = 0;                          // loop iterations
            long elapsed = 0;                           // elapsed time between iterations
            long start = System.currentTimeMillis();    // start time of iteration
            long minPs0Received = 8;                    // we consider finish when we recieve 8 'standby' status in sequence
            long ps0Received = 0;                       // count the number of 'standby' status returned in sequence

            mainLoop:
            while ((elapsed = System.currentTimeMillis() - start) < READ_TIMEOUT && printing) {
                logger.infof("Socket iteration '%d' (%d ms)", iteration, elapsed);
                try {
                    // request printer status and EPC/TID
                    this.CommandPGPK();

                    // read socket
                    final List<RawPacket> packets = connection.read();
                    logger.debugf("Socket read '%d' packets", packets.size());

                    for (RawPacket row : packets) {
                        logger.debugf("Socket row: '%s'", row);

                        final List<SatoMessage> messages = SatoParser.parse(row);
                        logger.debugf("Socket parsed messages '%d'", messages.size());

                        for (SatoMessage message : messages) {
                            if (message != null) {
                                if (message instanceof SatoMessage.PrinterInfo) {
                                    final SatoMessage.PrinterInfo obj = (SatoMessage.PrinterInfo) message;
                                    logger.debugf("Printer status message = %s", obj);

                                    // verifica se a impressao está finalizada, pois veio um ,PS0, no status da impressora
                                    if (obj.getPs() == PrinterStatus.STANDBY) {
                                        // força o monitoramento a receber 10 status PS0 antes de encerrar o loop, pois as vezes a impressora demora um pouco para mudar o status
                                        // nesse caso impressão de 1 ou 2 etiquetas podem ser sobrescritas pelo envio da próxima se o loop terminar rápido demaisø
                                        if (ps0Received >= minPs0Received) {
                                            printing = false;
                                            logger.info("Printing completed");
                                        }
                                        logger.infof("Socket PS0 received: %d", ps0Received);
                                        ps0Received++;
                                    }
                                } else if (message instanceof SatoMessage.RequestTag) {
                                    final SatoMessage.RequestTag obj = (SatoMessage.RequestTag) message;
                                    logger.debugf("Request tag message = %s", obj);

                                    final String epc = obj.getEpc();
                                    if (epc != null) {
                                        if (results.add(epc)) {
                                            onReceiveEpc(epc);
                                        }
                                    }
                                } else if (message instanceof SatoMessage.Ack) {
                                    final SatoMessage.Ack obj = (SatoMessage.Ack) message;
                                    logger.debugf("Ack message = %s", obj);
                                } else if (message instanceof SatoMessage.Nak) {
                                    final SatoMessage.Nak obj = (SatoMessage.Nak) message;
                                    logger.debugf("Nak message = %s", obj);
                                } else {
                                    logger.debugf("Unknown message type: %s", message);
                                }

                                // update last payload received
                                start = System.currentTimeMillis();
                            }

                            if (iteration > MAX_ITERATIONS) {
                                logger.errorf("Breaking from infinite loop");
                                break;
                            }

                            prev = message;
                        }
                    }
                } catch (InterruptedException e) {
                    logger.error("Failed to sleep", e);
                } catch (PrinterException e) {
                    logger.error("Failed to read socket", e);
                }

                iteration++;
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
        this.printing = false;
        // this.paused = false;
        // this.abort = false;
        // this.skus.clear();
    }

    @Override
    public void play() throws PrinterException {
        // DC2 + H = command starts printing.
        final String cmd = "\u0002\u0011H\u0003";

        try {
            connection.reconnect();

            logger.infof("Socket send: u0002, u0011H, u0003 (play printing)");

            // paused = false;

            // super.play();

            for (int i = 0; i < 3; i++) {
                connection.send(cmd);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    // ignore
                }
            }

            if (!printing) {
                connection.close();
            }
        } catch (IOException e) {
            throw new PrinterException(e);
        }
    }

    @Override
    public void pause() throws PrinterException {
        final String cmd = "\u0002\u0010H\u0003";

        try {
            connection.reconnect();

            logger.infof("Socket send: u0002, u0010H, u0003 (pause printing)");

            // paused = true;

            // super.pause();

            for (int i = 0; i < 3; i++) {
                connection.send(cmd);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    // ignore
                }
            }

            if (!printing) {
                connection.close();
            }
        } catch (IOException e) {
            throw new PrinterException(e, "Error pausing printer");
        }
    }

    /**
     * Cancel only the current label file.
     *
     * @throws IOException -- when socket connection is lost.
     */
    @Override
    public void cancel() throws PrinterException {
        // DC2 + PH = This command cancels print jobs and clears the entire contents of receive buffer.
        final String cmd = "\u0002\u0012PH\u0003";

        try {
            connection.reconnect();

            printing = false;

            // when printing one sku can be partitioned into multiple file ('content')
            // so when we cancel we mark that 'sku' to be skipped in the future
            boolean added = abortedSkus.add(currentSku);
            if (added) {
                logger.infof("Cancelling sku: %s", currentSku);
            }

            logger.info("Socket send: u0002, u0012PH, u0003 (cancel printing)");

            for (int i = 0; i < 5; i++) {
                connection.send(cmd);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    // ignore
                }
            }

            connection.close();
        } catch (IOException e) {
            throw new PrinterException(e, "Error cancelling printing");
        }
    }

    // COMMAND

    /**
     * Send command to printer to request printer status and EPC/TID
     *
     * @throws PrinterException
     */
    protected void CommandPGPK() throws PrinterException, InterruptedException {
        // DC2 + PG = command returns the printer status. (requires PK command to return, pg. 435)
        // DC2 + PK = command returns the status of RFID tag write by <IP0> command and EPC/TID. (pg. 444, 451)
        final String cmd = "\u0002\u0012PG\u0012PK\u0003";
        try {
            logger.infof("Socket send: u0002, u0012PG u0012PK u0003");
            connection.send(cmd);
            Thread.sleep(100);
        } catch (IOException e) {
            throw new PrinterException(e, "Error sending PG/PK commands");
        }
    }

    /**
     * Send command to printer to start printing.
     *
     * @throws PrinterException
     */
    protected void RequestPlay() throws PrinterException, InterruptedException {
        // DC2 + H = command starts printing. (pg. ?)
        final String cmd = "\u0002\u0011H\u0003";
        try {
            logger.infof("Socket send: u0002, u0011H, u0003 (play printing)");
            connection.send(cmd);
            Thread.sleep(100);
        } catch (IOException e) {
            throw new PrinterException(e, "Error sending H commands");
        }
    }

    /**
     * Send command to printer to pause printing.
     *
     * @throws PrinterException
     * @throws InterruptedException
     */
    protected void RequestPause() throws PrinterException, InterruptedException {
        final String cmd = "\u0002\u0010H\u0003";
        try {
            logger.infof("Socket send: u0002, u0010H, u0003 (pause printing)");
            connection.send(cmd);
            Thread.sleep(100);
        } catch (IOException e) {
            throw new PrinterException(e, "Error pausing printer");
        }
    }

    /**
     * Cancel request <DC2 + PH>
     *
     * @throws IOException -- when socket connection is lost.
     */
    protected void RequestCancel() throws PrinterException, InterruptedException {
        // DC2 + PH = This command cancels print jobs and clears the entire contents of receive buffer. (pg. 438)
        final String cmd = "\u0002\u0012PH\u0003";
        try {
            logger.info("Socket send: u0002, u0012PH, u0003 (cancel printing)");
            connection.send(cmd);
            Thread.sleep(500);
        } catch (IOException e) {
            throw new PrinterException(e, "Error cancelling printing");
        }
    }

    protected void Reset() throws PrinterException, InterruptedException {
        // DC2 + DC = This command resets the printer to its default state. (pg. 400)
        final String cmd = "\u0002\0012DC\u0003";
        try {
            logger.info("Socket send: u0002, u0012PH, u0003 (cancel printing)");
            connection.send(cmd);
            Thread.sleep(100);
        } catch (IOException e) {
            throw new PrinterException(e, "Error cancelling printing");
        }
    }

    protected void PowerOff() throws PrinterException, InterruptedException {
        // DC2 + DD = This command powers off the printer. (pg. 400)
        final String cmd = "\u0002\0012DD\u0003";
        try {
            logger.info("Socket send: u0002, u0012PH, u0003 (cancel printing)");
            connection.send(cmd);
            Thread.sleep(500);
        } catch (IOException e) {
            throw new PrinterException(e, "Error cancelling printing");
        }
    }

}
