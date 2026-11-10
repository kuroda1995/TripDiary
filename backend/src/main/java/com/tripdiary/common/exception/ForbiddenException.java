package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** 権限がない（403）。他人のリソースを操作しようとした場合など。 */
public class ForbiddenException extends ApiException {

    public static final String CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, CODE, message);
    }
}
