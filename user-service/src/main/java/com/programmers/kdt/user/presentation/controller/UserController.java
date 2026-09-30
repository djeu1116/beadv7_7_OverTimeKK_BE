package com.programmers.kdt.user.presentation.controller;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.user.presentation.dto.EmailVerificationConfirmRequest;
import com.programmers.kdt.user.presentation.dto.EmailVerificationRequest;
import com.programmers.kdt.user.presentation.dto.LoginRequest;
import com.programmers.kdt.user.presentation.dto.LoginResponse;
import com.programmers.kdt.user.presentation.dto.QueueEnterResponse;
import com.programmers.kdt.user.presentation.dto.QueueStatusResponse;
import com.programmers.kdt.user.presentation.dto.RefreshTokenRequest;
import com.programmers.kdt.user.presentation.dto.SignUpBusinessRequest;
import com.programmers.kdt.user.presentation.dto.SignUpIndividualRequest;
import com.programmers.kdt.user.presentation.dto.UserResponse;
import com.programmers.kdt.user.presentation.dto.WithdrawRequest;

import com.programmers.kdt.user.presentation.dto.EmailNotificationRequest;

import com.programmers.kdt.user.application.email.EmailVerificationService;
import com.programmers.kdt.user.application.queue.LoginQueueService;
import com.programmers.kdt.user.application.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final String ADMISSION_TOKEN_HEADER = "X-Admission-Token";

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final LoginQueueService loginQueueService;

    @PostMapping("/email/verification-codes")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/email/verification")
    public ApiResponse<Void> confirmVerificationCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.confirmVerificationCode(request.email(), request.code());
        return ApiResponse.success(null);
    }

    @PostMapping("/signup/individual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signUpIndividual(@Valid @RequestBody SignUpIndividualRequest request) {
        return ApiResponse.success(userService.signUpIndividual(request));
    }

    @PostMapping("/signup/business")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signUpBusiness(@Valid @RequestBody SignUpBusinessRequest request) {
        return ApiResponse.success(userService.signUpBusiness(request));
    }

    @PostMapping("/login/queue/enter")
    public ApiResponse<QueueEnterResponse> enterLoginQueue() {
        LoginQueueService.QueueEnterResult result = loginQueueService.enter();
        String status = result.admitted() ? "READY" : "WAITING";
        return ApiResponse.success(new QueueEnterResponse(status, result.token(), null));
    }

    @GetMapping("/login/queue/status")
    public ApiResponse<QueueStatusResponse> loginQueueStatus(@RequestParam String token) {
        LoginQueueService.QueueStatusResult result = loginQueueService.status(token);
        return ApiResponse.success(new QueueStatusResponse(result.status(), result.position()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestHeader(ADMISSION_TOKEN_HEADER) String admissionToken,
                                             @Valid @RequestBody LoginRequest request) {
        if (!loginQueueService.consumeAdmission(admissionToken)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        try {
            return ApiResponse.success(userService.login(request));
        } finally {
            loginQueueService.release(admissionToken);
        }
    }

    @PostMapping("/token/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(userService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        userService.logout(userId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                       @Valid @RequestBody WithdrawRequest request) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        userService.withdraw(userId, request);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 본인의 정보 조회. 프론트는 반드시 이 API만 사용 (아래 /{userId}를 직접 호출 금지)
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(userService.getUser(userId));
    }

    /**
     * 다른 서비스(order-service 등)가 REST로 회원 존재 여부를 확인할 때 쓰는 내부용 API
     * 프론트가 직접 호출하면 안 됨 (인증 없이 임의 userId 조회 가능 — 서비스 간 통신 전용)
     * TODO: 실제로는 이런 서비스 간 호출 엔드포인트를 명확히 구분(예: /internal/**)하는 것을 고려
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUser(userId));
    }

    @PostMapping("/{userId}/notifications/email")
    public ApiResponse<Void> sendNotificationEmail(
            @PathVariable Long userId,
            @Valid @RequestBody EmailNotificationRequest request) {

        userService.sendNotificationEmail(userId, request);
        return ApiResponse.success(null);
    }
}
