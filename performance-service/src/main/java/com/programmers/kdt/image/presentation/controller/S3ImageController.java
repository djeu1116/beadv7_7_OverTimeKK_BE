package com.programmers.kdt.image.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.image.presentation.dto.ImgUploadUrlRequest;
import com.programmers.kdt.image.presentation.dto.ImgUploadUrlResponse;
import com.programmers.kdt.image.application.service.S3ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/images")
@RestController
@RequiredArgsConstructor
public class S3ImageController {

    private final S3ImageService s3ImageService;

    @PostMapping("/upload-url")
    public ApiResponse<ImgUploadUrlResponse> createUploadUrl(@RequestBody ImgUploadUrlRequest request) {
        ImgUploadUrlResponse uploadUrl = s3ImageService.createUploadUrl(request.fileName(), request.contentType(), request.fileSize());
        return ApiResponse.success(uploadUrl);
    }
}
