package com.contare.printers.sato;

import com.contare.printers.sato.enums.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public abstract class SatoMessage {

    private final String raw;

    @Override
    public String toString() {
        return String.format("%s{raw = %s}", getClass().getSimpleName(), raw);
    }

    @EqualsAndHashCode(callSuper = false)
    public static class None extends SatoMessage {
        public None(final String raw) {
            super(raw);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    public static class Ack extends SatoMessage {
        public Ack(final String raw) {
            super(raw);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    public static class Nak extends SatoMessage {
        public Nak(final String raw) {
            super(raw);
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = false)
    public static class PrinterInfo extends SatoMessage {

        private final int bytes; // number of bytes between STX and ETX
        private final PrinterStatus ps; // printer status
        private final ReceiveBufferStatus rs; // receive buffer status
        private final RibbonStatus re; // ribbon status
        private final MediaStatus pe; // media status
        private final ErrorNumber en; // error number
        private final BatteryStatus bt; // battery status
        private final Integer q;  // remaining number of print

        public PrinterInfo(final String raw,
                           final int bytes,
                           final PrinterStatus ps,
                           final ReceiveBufferStatus rs,
                           final RibbonStatus re,
                           final MediaStatus pe,
                           final ErrorNumber en,
                           final BatteryStatus bt,
                           final Integer q) {
            super(raw);
            this.bytes = bytes;
            this.ps = ps;
            this.rs = rs;
            this.re = re;
            this.pe = pe;
            this.en = en;
            this.bt = bt;
            this.q = q;
        }

        @Override
        public String toString() {
            return String.format("%s{ bytes = %d, ps = %s, rs = %s, re = %s, pe = %s, en = %s, bt = %s, q = '%s', raw = '%s' }",
                getClass().getSimpleName(),
                bytes,
                ps,
                rs,
                re,
                pe,
                en,
                bt,
                q,
                getRaw()
            );
        }

    }

    @Getter
    @EqualsAndHashCode(callSuper = false)
    public static class TagInfo extends SatoMessage {

        private final int bytes; // data size, number of bytes between STX and ETX (max 5 digits)
        private final String wr; // write result status (0 = Write failure, 1 = Write success)
        private final String es; // error symbol (N = No error, E = EPC write error, T = TID write error, M = MCS error (Chip inconsistent or not supported), A = All errors)
        private final String epc; // epc hexadecimal string
        private final String tid; // tid hexadecimal string

        public TagInfo(final String raw,
                       final int bytes,
                       final String wr,
                       final String es,
                       final String epc,
                       final String tid) {
            super(raw);
            this.bytes = bytes;
            this.wr = wr;
            this.es = es;
            this.epc = epc;
            this.tid = tid;
        }

        @Override
        public String toString() {
            return String.format("%s{ bytes = %d, wr = %s, es = %s, epc = %s, tid =  %s, raw = %s }",
                getClass().getSimpleName(),
                bytes,
                wr,
                es,
                epc,
                tid,
                getRaw()
            );
        }

    }

}
