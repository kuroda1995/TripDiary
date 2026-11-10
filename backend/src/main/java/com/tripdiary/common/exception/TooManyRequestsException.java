package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** 試行回数の上限超過（429）。 */
public class TooManyRequestsException extends ApiException {

    public static final String CODE = "TOO_MANY_REQUESTS";

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
    }
}
