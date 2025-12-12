package com.contare.printers.core;

public interface PrinterConsumer<T> {

    void accept(final T t) throws PrinterException;

}
