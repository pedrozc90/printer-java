package com.contare.printers.zebra.objects;

import com.contare.printers.zebra.enums.RFIDOperation;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class ZebraPrinterInformation {

    private final RFIDData rfidData = new RFIDData();
    private final List<RFIDOperation> operationStatusList = new ArrayList<>();
    private List<String> epcs = new ArrayList<>();
    private boolean emptyReceived = false;
    private boolean startEndReceived = false;

    public static ZebraPrinterInformation parse(final String line) {
        ZebraPrinterInformation information = new ZebraPrinterInformation();
        information.setEmptyReceived(false);
        information.setStartEndReceived(false);

        if (StringUtils.isBlank(line)) {
            information.setStartEndReceived(true);
            return information;
        }

        // Divide pela quebra de linha e remove espaço em branco
        List<String> lines = Arrays.stream(line.split("\\r?\\n"))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // se só vier somente <start> <end> precisa chamar o hl de novo
        if (lines.size() <= 2) {
            if (lines.get(0).equals("<start>") && lines.get(1).equals("<end>")) {
                // information.setEmptyReceived(true);
                information.setStartEndReceived(true);
                return information;
            }
        }

        int startIndex = lines.indexOf("<start>");
        int endIndex = lines.indexOf("<end>");

        if (endIndex - startIndex > 0) {
            // se finalizar com ,3400| é porque imprimiu void
            List<String> linesRfidStatusWrite = lines.stream()
                .filter(strLine -> !strLine.contains(",3400|"))
                .map(strLine -> information.rfidData.isOperationInLine(strLine, RFIDOperation.WRITE))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (!linesRfidStatusWrite.isEmpty()) {
                List<RFIDData> rfidDataWrite = linesRfidStatusWrite.stream().map(information.rfidData::parse)
                    .collect(Collectors.toList());

                if (!rfidDataWrite.isEmpty()) {
                    information.epcs.addAll(rfidDataWrite.stream().map(RFIDData::getData).filter(Objects::nonNull).collect(Collectors.toList()));
                    information.operationStatusList.addAll(rfidDataWrite.stream().map(RFIDData::getOperationStatus).collect(Collectors.toList()));
                }
            }

            List<String> linesRfidStatusLock = lines.stream()
                .map(strLine -> information.rfidData.isOperationInLine(strLine, RFIDOperation.LOCK_UNLOCK_MEMORY_BANK))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (!linesRfidStatusLock.isEmpty()) {
                List<RFIDData> rfidDataLock = linesRfidStatusLock.stream().map(information.rfidData::parse)
                    .collect(Collectors.toList());

                if (!rfidDataLock.isEmpty()) {
                    information.operationStatusList.addAll(rfidDataLock.stream().map(RFIDData::getOperationStatus).collect(Collectors.toList()));
                }
            }

            if (!information.operationStatusList.contains(RFIDOperation.WRITE)) { // || !information.operationStatusList.contains(OperationStatus.LOCK_UNLOCK_MEMORY_BANK)
                // information.callHLAgain = true;
                return information;
            }

            // se ja existir write e lock, ja temos tudo que é necessario para prosseguir para a proxima etiqueta
            // information.callHLAgain = false;

            // read operation (pode ou não existir)
            List<String> linesRfidStatusRead = lines.stream()
                .map(strLine -> information.rfidData.isOperationInLine(strLine, RFIDOperation.READ))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (!linesRfidStatusRead.isEmpty()) {
                List<RFIDData> rfidDataRead = linesRfidStatusRead.stream().map(information.rfidData::parse)
                    .collect(Collectors.toList());

                if (!rfidDataRead.isEmpty()) {
                    information.operationStatusList.addAll(rfidDataRead.stream().map(RFIDData::getOperationStatus).collect(Collectors.toList()));
                }
            }

        }

        return information;
    }

}
