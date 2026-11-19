package com.tripdiary.auth.dto;

import com.tripdiary.auth.GuestAccount.Credentials;
import io.swagger.v3.oas.annotations.media.Schema;

/** ゲストのメールアドレスとパスワード（ログイン画面に表示する。docs/features/01_auth.md 4.8）。 */
@Schema(description = "ゲストのログイン情報")
public record GuestCredentialsResponse(
        @Schema(example = "haruka@example.com") String email,
        @Schema(example = "Demo1234") String password) {

    public static GuestCredentialsResponse from(Credentials credentials) {
        return new GuestCredentialsResponse(credentials.email(), credentials.password());
    }
}
