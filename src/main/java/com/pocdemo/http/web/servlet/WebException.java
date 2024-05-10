package com.pocdemo.http.web.servlet;

public class WebException extends RuntimeException {
    final int httpCode;

    public WebException(int httpCode, String msg) {
        super(msg);
        this.httpCode = httpCode;
    }

    public WebException(String msg) {
        super(msg);
        this.httpCode = 500;
    }

    public int getHttpCode() {
        return httpCode;
    }

}
