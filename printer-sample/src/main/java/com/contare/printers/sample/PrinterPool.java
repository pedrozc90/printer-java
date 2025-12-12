package com.contare.printers.sample;

import com.contare.printers.averydennison.AveryDennisonPrinter;
import com.contare.printers.core.Printer;
import com.contare.printers.sato.SatoPrinter;
import com.contare.printers.zebra.ZebraPrinter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PrinterPool {

    private static PrinterPool instance;

    private final Logger logger = Logger.getLogger(PrinterPool.class);

    private final Map<Pair<String, Integer>, Printer> pool = new ConcurrentHashMap<>();

    public static PrinterPool getInstance() {
        if (instance == null) {
            instance = new PrinterPool();
        }
        return instance;
    }

    public Map<Pair<String, Integer>, Printer> getPool() {
        return Collections.unmodifiableMap(pool);
    }

    public Printer factory(final String type, final String ip, final Integer port) {
        final String t = Objects.requireNonNull(type, "type must not be null");
        switch (t) {
            case "SATO":
                return new SatoPrinter(ip, port);
            case "ZEBRA":
                return new ZebraPrinter(ip, port);
            case "AVERY_DENNISON":
                return new AveryDennisonPrinter(ip, port);
            default:
                throw new IllegalArgumentException("Printer type " + t + " is not supported");
        }
    }

    public Printer get(final String ip, final Integer port) {
        final Pair<String, Integer> pair = new Pair<>(ip, port);
        return pool.get(pair);
    }

    public boolean exists(final String ip, final Integer port) {
        final Pair<String, Integer> pair = new Pair<>(ip, port);
        return pool.containsKey(pair);
    }

    public Printer put(final String ip, final Integer port, final Printer printer) {
        final Pair<String, Integer> pair = new Pair<>(ip, port);
        return pool.put(pair, printer);
    }

    public Printer remove(final String ip, final Integer port) {
        try {
            final Printer printer = get(ip, port);
            if (printer != null) {
                printer.close();
            }
            final Pair<String, Integer> pair = new Pair<>(ip, port);
            return pool.remove(pair);
        } catch (Exception e) {
            logger.errorf(e, "Error while closing printer: %s:%d", ip, port);
        }
        return null;
    }

    public void clear() {
        pool.forEach((k, v) -> {
            try {
                v.close();
            } catch (Exception e) {
                logger.errorf(e, "Error while closing printer: %s", v);
            }
        });
        pool.clear();
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pair<K, V> {

        private final K key;

        private final V value;

    }

}
