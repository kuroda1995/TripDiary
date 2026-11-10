package com.tripdiary.common.exception;

import com.tripdiary.common.logging.MdcKeys;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.slf4j.MDC;

/** 共通エラーレスポンス（docs/features/README.md「共通仕様」）。 */
@Schema(description = "共通エラーレスポンス")
public record ErrorResponse(
        @Schema(description = "エラーコード", example = "VALIDATION_ERROR") String code,
        @Schema(description = "利用者向けメッセージ", example = "入力内容に誤りがあります") String message,
        @Schema(description = "項目ごとのエラー（入力エラー時のみ要素あり）") List<FieldError> details,
        @Schema(description = "問い合わせ用の追跡ID（X-Request-Id と同じ値）") String requestId) {

    public record FieldError(
            @Schema(example = "title") String field,
            @Schema(example = "タイトルは必須です") String message) {}

    public static ErrorResponse of(String code, String message) {
        return of(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldError> details) {
        return new ErrorResponse(code, message, details, MDC.get(MdcKeys.REQUEST_ID));
    }
}
