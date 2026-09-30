package com.programmers.kdt.image.application.service;

import com.programmers.kdt.image.presentation.dto.ImgUploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ImageService {

    private static final Long IMG_MAX_SIZE = 3 * 1024 * 1024L ; // 3MB 제한

    private static final Duration UPLOAD_URL_EXPIRED = Duration.ofMinutes(1); // S3 업로드 URL 유효시간 1분
    private static final Duration ALLOWED_GET_URL_EXPIRED = Duration.ofMinutes(30);

    private static final Map<String, Set<String>> CONTENT_TYPE_TO_EXTENSIONS = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/jpg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png")
    );

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.image-prefix}")
    private String imagePrefix;

    public ImgUploadUrlResponse createUploadUrl(
            String originalFileName, String contentType, Long fileSize) {

        validateFileSize(fileSize);
        String extension = extractExtension(originalFileName);
        validateContentType(contentType, extension);

        String objectKey = imagePrefix + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(fileSize)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_EXPIRED)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new ImgUploadUrlResponse(
                objectKey,
                presignedRequest.url().toString()
        );
    }

    private void validateContentType(String contentType, String extension) {
        Set<String> allowedExtensions = CONTENT_TYPE_TO_EXTENSIONS.get(contentType);

        if (allowedExtensions == null) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }

        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("실제 확장자와 Content-Type이 일치하지 않습니다.");
        }
    }

    private void validateFileSize(Long fileSize) {
        if (fileSize >= IMG_MAX_SIZE) {
            throw new IllegalArgumentException("이미지 최대 크기는 3MB입니다.");
        }
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null) {
            throw new IllegalArgumentException("파일명을 입력해주세요.");
        }

        int dotIndex = originalFileName.lastIndexOf(".");

        if (dotIndex < 0) {
            throw new IllegalArgumentException("확장자명이 존재하지 않습니다.");
        }

        return originalFileName.substring(dotIndex).toLowerCase();
    }

    public String getPresignedGetUrl(String objectKey) {

        if (objectKey == null || objectKey.isEmpty()) {
            return "";
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(ALLOWED_GET_URL_EXPIRED)
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }

}