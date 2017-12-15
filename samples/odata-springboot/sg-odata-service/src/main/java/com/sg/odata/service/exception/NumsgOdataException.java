package com.sg.odata.service.exception;

import org.apache.olingo.commons.api.ex.ODataException;

/**
 * Created by gaoqiang on 2017/4/26.
 */
public class NumsgOdataException extends ODataException {

    public NumsgOdataException(String msg) {
        super(msg);
    }

    public NumsgOdataException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
