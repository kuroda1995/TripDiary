package com.tripdiary.photo;

import com.tripdiary.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** S3 への接続・署名生成の失敗（500 STORAGE_ERROR。ERROR ログを出す）。 */
public class StorageException extends ApiException {

    public static final String CODE = "STORAGE_ERROR";

    public StorageException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, CODE, "写真の保存先に接続できませんでした。時間をおいて再度お試しください");
        initCause(cause);
    }
}
