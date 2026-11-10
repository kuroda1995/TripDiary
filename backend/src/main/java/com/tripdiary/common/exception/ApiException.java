package com.tripdiary.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 業務例外の基底クラス。{@link GlobalExceptionHandler} が共通エラーレスポンスに変換する。
 *
 * <p>エラーコードは機能別ドキュメント（docs/features/*.md）の「エラーコード」に合わせる。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
