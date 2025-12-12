package com.contare.printers.zebra;

import com.contare.printers.core.BasePrinter;
import com.contare.printers.core.PrinterException;
import com.contare.printers.core.RawPacket;
import com.contare.printers.zebra.enums.RFIDOperation;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ZebraPrinter extends BasePrinter {

    public ZebraPrinter(final String ip, final Integer port) {
        super(ip, port);
    }

    @Override
    protected String createLabel(final String ip, final Integer port) {
        return String.format("zebra@%s:%d", ip, port);
    }

    @Override
    public Set<String> send(final String content, final String sku, final Integer epcs) throws PrinterException {
        final Set<String> results = new TreeSet<>();

        currentSku = sku;

        try {
            // make sure the printer is connected
            connection.reconnect();

            // cancelar a impressao de todos os sku de uma impressão
            if (abortedSkus.contains(currentSku)) {
                logger.infof("Aborting printing -> sku: %s", currentSku);
                return results;
            }

            // make sure the printer is connected
            connection.reconnect();

            this.printing = true;                   // marcado como false quando a impressão é cancelada ou quando recebemos o comando de finalização.

            this.CancelCmd();                          // cancela jobs e limpa buffer para começar a impressão
            this.play();                            // despausa caso a impressora esteja em pausa
            this.sendXAHLXZ();                      // limpa o buffer em firmware antigo e novo

            logger.infof("Send content -> sku: %s - number of epcs: %d", sku, epcs);
            logger.info("------------------------------------------------------------");
            logger.info(content);
            logger.info("------------------------------------------------------------");

            connection.send(content);

            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                // ignored
            }

            ZebraMessage prev = null;
            int iteration = 0;                      // loop iterations
            long elapsed = 0L;
            long last = System.currentTimeMillis();
            long minStartEndReceived = 8;                // numero minimo de vezes que deve-se receber o PS0 para considerar a impressão concluída
            long startEndReceived = 0;                   // numero de vezes que não recebemos nada da impressora

            // para verificar se ja imprimiu
            this.sendHL();

            mainLoop:
            while ((elapsed = System.currentTimeMillis() - last) < READ_TIMEOUT && printing) {
                logger.infof("Socket iteration: %d - elapsed time: %d ms", iteration, elapsed);
                try {
                    final List<RawPacket> packets = connection.read();
                    logger.debugf("Socket read '%d' lines", packets.size());

                    for (RawPacket packet : packets) {
                        logger.debugf("Socket packet = %s", packet);

                        final List<ZebraMessage> messages = ZebraParser.parse(packet);
                        logger.debugf("Socket parsed '%d' messages", messages.size());

                        for (ZebraMessage message : messages) {
                            logger.debugf("Socket message = %s", message);

                            if (message instanceof ZebraMessage.RFIDData) {
                                final ZebraMessage.RFIDData obj = (ZebraMessage.RFIDData) message;
                                if (obj.getOperation() == RFIDOperation.WRITE) {
                                    final String epc = obj.getData();
                                    if (epc != null && results.add(epc)) {
                                        onReceiveEpc(epc);
                                    }
                                }
                            } else {
                                logger.warnf("Unknown message = %s", message);
                            }

                            prev = message;
                        }

                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ignored) {
                            // ignore
                        }

                        this.sendHL();

                        // atualiza a ultima leitura recebida
                        last = System.currentTimeMillis();

                        if (iteration > MAX_ITERATIONS) {
                            logger.error("Break free from infinite loop infinito");
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to read socket", e);
                }

                iteration++;
            }
        } catch (IOException e) {
            throw new PrinterException(e, "Error printing");
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
        this.printing = false;
        this.paused = false;
        // this.abort = false;
        // this.skus.clear();
    }

    @Override
    public void play() throws PrinterException {
        final String cmd = "~PS";
        try {
            logger.debugf("Socket send: '%s'", cmd);
            connection.send(cmd);
            paused = false;
            // super.play();
        } catch (Exception e) {
            throw new PrinterException(e, "Error playing printer");
        }
    }

    @Override
    public void pause() throws PrinterException {
        final String cmd = "~PP";
        try {
            logger.debugf("Socket send: '%s'", cmd);
            connection.send(cmd);
            paused = true;
            // super.pause();
        } catch (Exception e) {
            throw new PrinterException(e, "Error pausing printer");
        }
    }

    @Override
    public void cancel() throws PrinterException {
        try {
            connection.reconnect();

            printing = false;

            logger.info("Socket send - cancel printing: ~JA");

            this.CancelCmd();

            connection.close();
        } catch (IOException e) {
            throw new PrinterException(e, "Error cancelling printing");
        }
    }

    // COMMANDS
    private void sendHL() throws PrinterException {
        final String cmd = "~HL";
        try {
            connection.reconnect();
            logger.infof("Socket send: '%s'", cmd);
            connection.send(cmd);
        } catch (Exception e) {
            throw new PrinterException(e, "Error sending %s", cmd);
        }
    }

    private void sendXAHLXZ() throws PrinterException {
        final String cmd = "^XA^HL^XZ";
        try {
            connection.reconnect();
            logger.infof("Socket send: '%s'", cmd);
            connection.send(cmd);
        } catch (Exception e) {
            throw new PrinterException(e, "Error sending HL");
        }
    }

    /**
     * Command cancels all format commands in the buffer. It also cancels any batches that are printing. (pg. 229)
     *
     * @throws PrinterException
     */
    protected void CancelCmd() throws PrinterException {
        final String cmd = "~JA";
        try {
            connection.reconnect();
            logger.infof("Socket send: '%s'", cmd);
            connection.send(cmd);
        } catch (Exception e) {
            throw new PrinterException(e, "Error sending '%s'", cmd);
        }
    }

}
