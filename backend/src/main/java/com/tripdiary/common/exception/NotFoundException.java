package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/** リソースが存在しない（404）。コード例: {@code SPOT_NOT_FOUND}。 */
public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
