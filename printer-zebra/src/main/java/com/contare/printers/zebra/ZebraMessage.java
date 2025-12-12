package com.contare.printers.zebra;

import com.contare.printers.zebra.enums.RFIDOperation;
import com.contare.printers.zebra.enums.RFIDStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

public interface ZebraMessage {

    @Data
    @AllArgsConstructor
    class RFIDData  implements ZebraMessage {

        private String raw;

        private String datetime; // a time stamp for the log entry (some older versions of firmware, this parameter does not display)
        private RFIDOperation operation;
        private String position; // program position
        private String antenna; // antenna element
        private String power; // read & write power
        private RFIDStatus status;
        private String data;

    }

}
