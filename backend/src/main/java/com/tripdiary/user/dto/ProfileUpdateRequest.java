package com.tripdiary.user.dto;

import com.tripdiary.common.validation.NotBlankIfPresent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import jakarta.validation.valueextraction.Unwrapping;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * プロフィール更新リクエスト（docs/features/01_auth.md 4.6）。変更する項目だけを送る。
 *
 * <p>各項目は「送らない（変更しない）」と「null を送る（消す）」を区別する。
 *
 * <ul>
 *   <li>項目を送らない → フィールドは Java の null のまま（setter が呼ばれない）
 *   <li>JSON の null → {@link Optional#empty()}
 *   <li>値あり → その値の {@link Optional}
 * </ul>
 *
 * <p>record のコンストラクタで受けると、項目が無い場合も {@link Optional#empty()} になり区別できないため、setter で受ける。
 */
@Schema(description = "プロフィール更新リクエスト（変更する項目のみ）")
@Getter
@Setter
@NoArgsConstructor // 全項目のコンストラクタを作ると Jackson がそれを使い、送らなかった項目が Optional.empty() になる
public class ProfileUpdateRequest {

    @Schema(description = "表示名（1〜50文字。null は不可）", example = "タロウ", implementation = String.class)
    @NotBlankIfPresent(message = "表示名を入力してください", payload = Unwrapping.Skip.class)
    private Optional<@Size(max = 50, message = "表示名は50文字以内で入力してください") String> displayName;

    @Schema(
            description = "自己紹介（500文字まで。null または空文字で削除）",
            example = "週末は温泉めぐり♨",
            nullable = true,
            implementation = String.class)
    private Optional<@Size(max = 500, message = "自己紹介は500文字以内で入力してください") String> bio;

    @Schema(
            description = "アイコンの S3 キー（purpose=AVATAR で発行したもの。null で削除）",
            example = "avatars/42/3b1f....webp",
            nullable = true,
            implementation = String.class)
    private Optional<@Size(max = 512) String> avatarS3Key;
}
