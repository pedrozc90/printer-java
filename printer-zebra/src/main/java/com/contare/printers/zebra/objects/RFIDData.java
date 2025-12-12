package com.contare.printers.zebra.objects;

import com.contare.printers.zebra.enums.RFIDOperation;
import com.contare.printers.zebra.enums.RFIDStatus;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
public class RFIDData {

    private LocalDateTime date;                         // só existe em firmares mais novos, inicialmente trabalhamos na versao que do firmware da impressora enviada pela tyco
    private RFIDOperation operationStatus;
    private String programPosition;
    private String antennaElement;
    private String power;
    private RFIDStatus status;
    private String data;                                // Manual chama de data, mas é neste campo que vem o epc

    public RFIDData parse(final String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }

        List<String> fields = Arrays.asList(line.split(","));

        if (!fields.get(0).equals(RFIDOperation.RFID_SETTINGS.getCode())) {
            if (fields.size() == 3) {
                // 110ix4
                setOperationStatus(RFIDOperation.parse(fields.get(0)));
                setStatus(RFIDStatus.parse(fields.get(1)));

                if (getOperationStatus().equals(RFIDOperation.WRITE)) {
                    setData(fields.get(2));
                }
            } else {
                // zt410
                setOperationStatus(RFIDOperation.parse(fields.get(0)));
                setProgramPosition(fields.get(1));
                setAntennaElement(fields.get(2));
                setPower(fields.get(3));
                setStatus(RFIDStatus.parse(fields.get(4)));

                // NO_TAG_FOUND que a impressora não conseguiu ler a tag na etiqueta e deve ter impresso void,
                // WRITE_FAILED garantirmos que não deu erro de escrita
                // RFID_OK é que não deu nenhum erro de impressão
                if (getOperationStatus().equals(RFIDOperation.WRITE) && getStatus().equals(RFIDStatus.RFID_OK)) {
                    setData(fields.get(5));
                }
            }
        }

        return this;
    }

    public String isOperationInLine(final String line, final RFIDOperation operationStatus) {
        if (StringUtils.isBlank(line)) {
            return null;
        }

        List<String> fields = Arrays.asList(line.split(","));
        if (fields.get(0).equals(operationStatus.getCode())) {
            return line;
        }
        return null;
    }

}
