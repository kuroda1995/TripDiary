package com.tripdiary.photo;

import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.photo.PhotoStorage.PresignedUpload;
import com.tripdiary.photo.dto.UploadUrlRequest;
import com.tripdiary.photo.dto.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 署名付き URL の発行（docs/features/03_photo_upload.md 5.1）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    static final String UNSUPPORTED_CONTENT_TYPE = "UNSUPPORTED_CONTENT_TYPE";
    static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    private final PhotoStorage photoStorage;

    public UploadUrlResponse issueUploadUrl(long userId, UploadUrlRequest request) {
        PhotoFormat format =
                PhotoFormat.ofContentType(request.contentType())
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                UNSUPPORTED_CONTENT_TYPE,
                                                "対応していない画像形式です（JPEG / PNG / WebP / HEIC のみ）"));
        UploadPurpose purpose = request.purpose();
        if (request.contentLength() > purpose.maxBytes()) {
            throw new BadRequestException(
                    FILE_TOO_LARGE,
                    "画像のサイズは%dMBまでです".formatted(purpose.maxBytes() / (1024 * 1024)));
        }
        String key = purpose.newKey(userId, format);
        PresignedUpload upload =
                photoStorage.presignPut(key, format.contentType(), request.contentLength());
        // 署名付き URL は認証情報と同等のためログに出さない
        log.info("Upload URL issued purpose={} key={}", purpose, key);
        return new UploadUrlResponse(upload.url(), key, upload.expiresAt());
    }
}
