package com.tripdiary.user;

import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.photo.PhotoFilesDiscardedEvent;
import com.tripdiary.photo.PhotoFormat;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.photo.UploadPurpose;
import com.tripdiary.user.dto.MeResponse;
import com.tripdiary.user.dto.ProfileUpdateRequest;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ログイン中のユーザーのプロフィール（docs/features/01_auth.md 4.5〜4.6）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    static final String INVALID_AVATAR_KEY = "INVALID_AVATAR_KEY";

    private final UserRepository userRepository;
    private final PhotoStorage photoStorage;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public MeResponse me(long userId) {
        return toResponse(find(userId));
    }

    /** 送られた項目だけを更新する。アイコンを差し替え・削除した場合、古いファイルはコミット後に S3 から削除する。 */
    @Transactional
    public MeResponse updateProfile(long userId, ProfileUpdateRequest request) {
        User user = find(userId);
        if (request.getDisplayName() != null) {
            // 空・null は Bean Validation で弾いている
            user.changeDisplayName(request.getDisplayName().orElseThrow().strip());
        }
        if (request.getBio() != null) {
            user.changeBio(
                    request.getBio().map(String::strip).filter(s -> !s.isEmpty()).orElse(null));
        }
        if (request.getAvatarS3Key() != null) {
            String next = request.getAvatarS3Key().orElse(null);
            if (!Objects.equals(next, user.getAvatarS3Key())) {
                if (next != null) {
                    verifyAvatar(userId, next);
                }
                String previous = user.changeAvatar(next);
                if (previous != null) {
                    eventPublisher.publishEvent(new PhotoFilesDiscardedEvent(List.of(previous)));
                }
            }
        }
        log.info("Profile updated userId={}", userId);
        return toResponse(user);
    }

    /** 自分がアイコン用に発行したキーで、S3 上に対応形式・サイズ内のファイルがあること。 */
    private void verifyAvatar(long userId, String s3Key) {
        boolean valid =
                UploadPurpose.AVATAR.isOwnKey(s3Key, userId)
                        && photoStorage
                                .head(s3Key)
                                .filter(o -> PhotoFormat.ofContentType(o.contentType()).isPresent())
                                .filter(o -> o.size() <= UploadPurpose.AVATAR.maxBytes())
                                .isPresent();
        if (!valid) {
            throw new BadRequestException(INVALID_AVATAR_KEY, "アイコン画像を確認できませんでした。もう一度アップロードしてください");
        }
    }

    private User find(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND, "ユーザーが存在しません"));
    }

    private MeResponse toResponse(User user) {
        return MeResponse.from(user, photoStorage::publicUrl);
    }
}
