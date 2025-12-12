package com.contare.printers.sato.driver;

import com.contare.printers.sato.enums.*;
import lombok.AllArgsConstructor;
import lombok.Data;

public interface SatoMessage {

    @Data
    @AllArgsConstructor
    class Ack implements SatoMessage {

        private String raw;

    }

    @Data
    @AllArgsConstructor
    class Nak implements SatoMessage {

        private String raw;

    }

    @Data
    @AllArgsConstructor
    class PrinterInfo implements SatoMessage {

        private String raw;

        private int bytes; // number of bytes between STX and ETX
        private PrinterStatus ps; // printer status
        private ReceiveBufferStatus rs; // receive buffer status
        private RibbonStatus re; // ribbon status
        private MediaStatus pe; // media status
        private ErrorNumber en; // error number
        private BatteryStatus bt; // battery status
        private String q;  // remaining number of print

    }

    @Data
    @AllArgsConstructor
    class RequestTag implements SatoMessage {

        private String raw;

        private int bytes; // data size, number of bytes between STX and ETX (max 5 digits)
        private String wr; // write result status (0 = Write failure, 1 = Write success)
        private String es; // error symbol (N = No error, E = EPC write error, T = TID write error, M = MCS error (Chip inconsistent or not supported), A = All errors)
        private String epc; // epc hexadecimal string
        private String tid; // tid hexadecimal string

    }

}
