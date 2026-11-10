package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** Bean Validation では表現できない業務ルール違反（400）。コード例: {@code CANNOT_FOLLOW_SELF}。 */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
