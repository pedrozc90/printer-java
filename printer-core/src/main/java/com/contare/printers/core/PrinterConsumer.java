package com.contare.printers.core;

import com.contare.printers.core.exceptions.PrinterException;

public interface PrinterConsumer<T> {

    void accept(final T t) throws PrinterException;

}
