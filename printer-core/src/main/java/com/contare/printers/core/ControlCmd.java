package com.contare.printers.core;

public class ControlCmd {

    public static final int EOF = -1;   // End of File (decimal = -1)
    public static final int STX = 0x02; // Start of Text (decimal = 2, unicode = '\\u0002')
    public static final int ETX = 0x03; // End of Text (decimal = 3, unicode = '\\u0003')
    public static final int ACK = 0x06; // Acknowledge (decimel = 6, unicode = '\\u0006')
    public static final int NAK = 0x15; // Not Acknowledge (decimal = 15, unicode = '\\u0015')
    public static final int CR = 0x0D;  // Carriage Return (decimal = 13, unicode = '\\u000D')
    public static final int LF = 0x0A;  // Line Feed ( decimal = 10, unicode = '\\u000A')

}
