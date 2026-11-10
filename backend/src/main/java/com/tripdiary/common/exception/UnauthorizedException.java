package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** 業務処理の中で判明した認証エラー（401）。コード例: {@code INVALID_CREDENTIALS}。 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
