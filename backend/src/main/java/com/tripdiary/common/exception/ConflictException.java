package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** 一意制約などの競合（409）。コード例: {@code EMAIL_ALREADY_EXISTS}。 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
