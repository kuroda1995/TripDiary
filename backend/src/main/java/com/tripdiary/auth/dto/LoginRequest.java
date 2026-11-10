package com.tripdiary.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** ログインリクエスト。 */
@Schema(description = "ログインリクエスト")
public record LoginRequest(
        @Schema(example = "taro@example.com") @NotBlank(message = "メールアドレスを入力してください") String email,
        @Schema(example = "Passw0rd123") @NotBlank(message = "パスワードを入力してください") String password) {

    /** パスワードをログ等に出さない。 */
    @Override
    public String toString() {
        return "LoginRequest{}";
    }
}
