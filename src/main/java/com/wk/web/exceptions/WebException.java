package com.wk.web.exceptions;

/**
 * Created by 005689 on 2016/12/6.
 */
public class WebException extends RuntimeException {
    public WebException() {
    }

    public WebException(String msg) {
        super(msg);
    }
}
