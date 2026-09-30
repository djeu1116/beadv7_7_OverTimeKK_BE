package com.programmers.kdt.image.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record ImgUploadUrlRequest(
        @NotNull String fileName,
        @NotNull String contentType,
        @NotNull Long fileSize
) {
}
