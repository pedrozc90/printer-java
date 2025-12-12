package com.contare.printers.sato.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum ErrorNumber {

    ONLINE(0, "N000", "00: Online - Not an error. Return is performed"),
    OFFLINE(1, "N001", "01: Offline - Not an error. Return is performed"),
    MACHINE_ERROR(2, "N002", "02: Machine error"),
    MEMORY_ERROR(3, "N003", "03: Memory error"),
    PROGRAM_ERROR(4, "N004", "04: Program error"),
    SETTING_INFO_FLASH_ERROR(5, "N005", "05: Setting information error (FLASH-ROM error)"),
    SETTING_INFO_EEPROM_ERROR(6, "N006", "06: Setting information error (EE-PROM error)"),
    DOWNLOAD_ERROR(7, "N007", "07: Download error"),
    PARITY_ERROR(8, "N008", "08: Parity error"),
    OVER_RUN(9, "N009", "09: Over run"),
    FRAMING_ERROR(10, "N010", "10: Framing error"),
    LAN_TIMEOUT_ERROR(11, "N011", "11: LAN timeout error"),
    BUFFER_OVER(12, "N012", "12: Buffer over"),
    HEAD_OPEN(13, "N013", "13: Head open"),
    PAPER_END(14, "N014", "14: Paper end"),
    RIBBON_END(15, "N015", "15: Ribbon end"),
    MEDIA_ERROR(16, "N016", "16: Media error"),
    SENSOR_ERROR(17, "N017", "17: Sensor error"),
    PRINTHEAD_ERROR(18, "N018", "18: Printhead error"),
    COVER_OPEN_ERROR(19, "N019", "19: Cover open error"),
    MEMORY_CARD_TYPE_ERROR(20, "N020", "20: Memory/Card type error"),
    MEMORY_CARD_READ_WRITE_ERROR(21, "N021", "21: Memory/Card read/write error"),
    MEMORY_CARD_FULL_ERROR(22, "N022", "22: Memory/Card full error"),
    MEMORY_CARD_NO_BATTERY_ERROR(23, "N023", "23: Memory/Card no battery error"),
    RIBBON_SAVER_ERROR(24, "N024", "24: Ribbon saver error"),
    CUTTER_ERROR(25, "N025", "25: Cutter error"),
    CUTTER_SENSOR_ERROR(26, "N026", "26: Cutter sensor error"),
    STACKER_FULL_ERROR(27, "N027", "27: Stacker full error"),
    COMMAND_ERROR(28, "N028", "28: Command error"),
    SENSOR_ERROR_AT_POWER_ON(29, "N029", "29: Sensor error at Power-On"),
    RFID_TAG_ERROR(30, "N030", "30: RFID tag error"),
    INTERFACE_CARD_ERROR(31, "N031", "31: Interface card error"),
    REWINDER_ERROR(32, "N032", "32: Rewinder error"),
    OTHER_ERROR(33, "N033", "33: Other error"),
    RFID_CONTROL_ERROR(34, "N034", "34: RFID control error"),
    HEAD_DENSITY_ERROR(35, "N035", "35: Head density error"),
    KANJI_DATA_ERROR(36, "N036", "36: Kanji data error"),
    CALENDAR_ERROR(37, "N037", "37: Calendar error"),
    ITEM_NO_ERROR(38, "N038", "38: Item No error"),
    BCC_ERROR(39, "N039", "39: BCC error"),
    CUTTER_COVER_OPEN_ERROR(40, "N040", "40: Cutter cover open error"),
    RIBBON_REWIND_NON_LOCK_ERROR(41, "N041", "41: Ribbon rewind non-lock error"),
    COMMUNICATION_TIMEOUT_ERROR(42, "N042", "42: Communication timeout error"),
    LID_LATCH_OPEN_ERROR(43, "N043", "43: Lid latch open error"),
    NO_MEDIA_ERROR_AT_POWER_ON(44, "N044", "44: No media error at Power-On"),
    SD_CARD_ACCESS_ERROR(45, "N045", "45: SD card access error"),
    SD_CARD_FULL_ERROR(46, "N046", "46: SD card full error"),
    HEAD_LIFT_ERROR(47, "N047", "47: Head lift error"),
    HEAD_OVERHEAT_ERROR(48, "N048", "48: Head overheat error"),
    SNTP_TIME_CORRECTION_ERROR(49, "N049", "49: SNTP time correction error"),
    CRC_ERROR(50, "N050", "50: CRC error"),
    CUTTER_MOTOR_ERROR(51, "N051", "51: Cutter motor error"),
    WLAN_MODULE_ERROR(52, "N052", "52: WLAN module error"),
    SCANNER_READING_ERROR(53, "N053", "53: Scanner reading error"),
    SCANNER_CHECKING_ERROR(54, "N054", "54: Scanner checking error"),
    SCANNER_CONNECTION_ERROR(55, "N055", "55: Scanner connection error"),
    BLUETOOTH_MODULE_ERROR(56, "N056", "56: BluetoothModule error"),
    EAP_AUTHENTICATION_FAILED(57, "N057", "57: EAP authentication error (EAP failed)"),
    EAP_AUTHENTICATION_TIMEOUT(58, "N058", "58: EAP authentication error (Time out)"),
    BATTERY_ERROR(59, "N059", "59: Battery error"),
    LOW_BATTERY(60, "N060", "60: Low Battery error"),
    LOW_BATTERY_CHARGING(61, "N061", "61: Low Battery error (Charging)"),
    BATTERY_NOT_INSTALLED(62, "N062", "62: Battery not installed error"),
    BATTERY_TEMPERATURE_ERROR(63, "N063", "63: Battery temperature error"),
    BATTERY_DETERIORATION_ERROR(64, "N064", "64: Battery deterioration error"),
    MOTOR_TEMPERATURE_ERROR(65, "N065", "65: Motor temperature error"),
    INSIDE_CHASSIS_TEMPERATURE_ERROR(66, "N066", "66: Inside chassis temperature error"),
    JAM_ERROR(67, "N067", "67: Jam error"),
    SIPL_FIELD_FULL_ERROR(68, "N068", "68: SIPL Field full error"),
    POWER_OFF_WHEN_CHARGING_ERROR(69, "N069", "69: Power off error when charging"),
    WLAN_MODULE_ERROR_DUPLICATE(70, "N070", "70: WLAN module error (duplicate entry in table)"),
    OPTION_MISMATCH_ERROR(71, "N071", "71: Option mismatch error"),
    BATTERY_DETERIORATION_NOTICE(72, "N072", "72: Battery deterioration error (Notice)"),
    BATTERY_DETERIORATION_WARNING(73, "N073", "73: Battery deterioration error (Warning)"),
    POWER_OFF_ERROR(74, "N074", "74: Power off error"),
    NONRFID_WARNING_ERROR(75, "N075", "75: NonRFID Warning Error"),
    BARCODE_READER_CONNECTION_ERROR(76, "N076", "76: Barcode reader connection error"),
    BARCODE_READING_ERROR(77, "N077", "77: Barcode reading error"),
    BARCODE_VERIFICATION_ERROR(78, "N078", "78: Barcode verification error"),
    BARCODE_READING_VERIFICATION_POSITION_ERROR(79, "N079", "79: Barcode reading error (Verification start position abnormality)");

    private final int number;
    private final String code;
    private final String description;


    private static final Map<Integer, ErrorNumber> _numbers = new HashMap<>();
    private static final Map<String, ErrorNumber> _codes = new HashMap<>();

    static {
        for (ErrorNumber row : values()) {
            _numbers.put(row.number, row);
            _codes.put(row.code, row);
        }
    }

    public static ErrorNumber get(final Integer value) {
        if (value == null) return null;
        return _numbers.get(value);
    }

    public static ErrorNumber get(final String value) {
        if (value == null) return null;
        return _codes.get(value);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, description);
    }

}
