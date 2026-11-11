package com.tripdiary.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** パスワード変更リクエスト（docs/features/01_auth.md 4.7）。 */
@Schema(description = "パスワード変更リクエスト")
public record PasswordChangeRequest(
        @Schema(description = "現在のパスワード") @NotBlank(message = "現在のパスワードを入力してください")
                String currentPassword,
        @Schema(description = "新しいパスワード（新規登録と同じ条件）")
                @NotNull(message = "新しいパスワードを入力してください")
                @Pattern(
                        regexp = SignupRequest.PASSWORD_PATTERN,
                        message = SignupRequest.PASSWORD_MESSAGE)
                String newPassword) {

    /** パスワードをログ等に出さない。 */
    @Override
    public String toString() {
        return "PasswordChangeRequest{}";
    }
}
