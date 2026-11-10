package com.tripdiary.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新規登録リクエスト（docs/features/01_auth.md 5章）。 */
@Schema(description = "新規登録リクエスト")
public record SignupRequest(
        @Schema(example = "taro@example.com")
                @NotBlank(message = "メールアドレスを入力してください")
                @Email(message = "メールアドレスの形式が正しくありません")
                @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
                String email,
        @Schema(description = "半角英数字と _ で3〜30文字。登録後は変更できない", example = "taro_trip")
                @NotNull(message = "ユーザー名を入力してください")
                @Pattern(regexp = USERNAME_PATTERN, message = "ユーザー名は半角英数字と _ で3〜30文字で入力してください")
                String username,
        @Schema(example = "旅するタロウ")
                @NotBlank(message = "表示名を入力してください")
                @Size(max = 50, message = "表示名は50文字以内で入力してください")
                String displayName,
        @Schema(description = "8〜72文字の半角英数字・記号。英字と数字をそれぞれ1文字以上含む", example = "Passw0rd123")
                @NotNull(message = "パスワードを入力してください")
                @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
                String password) {

    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_]{3,30}$";

    /** BCrypt は72バイトまでしか扱えないため、1文字1バイトの半角文字に限定する。 */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,72}$";

    public static final String PASSWORD_MESSAGE = "パスワードは8〜72文字の半角英数字・記号で、英字と数字をそれぞれ1文字以上含めてください";

    /** パスワードをログ等に出さない。 */
    @Override
    public String toString() {
        return "SignupRequest{username=" + username + "}";
    }
}
