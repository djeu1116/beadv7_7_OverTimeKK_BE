package com.programmers.kdt.user.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.presentation.dto.LoginRequest;
import com.programmers.kdt.user.presentation.dto.LoginResponse;
import com.programmers.kdt.user.presentation.dto.RefreshTokenRequest;
import com.programmers.kdt.user.presentation.dto.SignUpBusinessRequest;
import com.programmers.kdt.user.presentation.dto.SignUpIndividualRequest;
import com.programmers.kdt.user.presentation.dto.UserResponse;
import com.programmers.kdt.user.presentation.dto.WithdrawRequest;
import com.programmers.kdt.user.presentation.dto.EmailNotificationRequest;
import com.programmers.kdt.user.infrastructure.email.EmailSender;
import com.programmers.kdt.user.application.email.EmailVerificationService;
import com.programmers.kdt.user.domain.entity.User;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import com.programmers.kdt.common.jwt.JwtProvider;
import com.programmers.kdt.user.infrastructure.jwt.RefreshTokenStore;
import com.programmers.kdt.user.infrastructure.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 인터페이스 없이 클래스 하나로 (팀 요청: MVC 단순화).
 * 모킹/테스트 대체 용이성 이슈 없음 — 의존 대상(UserRepository/PasswordEncoder는 인터페이스, JwtProvider는 non-final 클래스)이라
 * 인터페이스 없이도 Mockito로 그대로 모킹 가능함을 확인함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserQueryService userQueryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;

    @Transactional
    public UserResponse signUpIndividual(SignUpIndividualRequest request) {
        emailVerificationService.requireVerifiedEmail(request.email());
        validateDuplicate(request.username(), request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.signUpIndividual(request.username(), request.email(), encodedPassword);
        User saved = saveUser(user);

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse signUpBusiness(SignUpBusinessRequest request) {
        emailVerificationService.requireVerifiedEmail(request.email());
        validateDuplicate(request.username(), request.email());
        validateDuplicateBusinessNumber(request.businessNumber());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.signUpBusiness(request.username(), request.email(), encodedPassword,
                request.businessName(), request.businessNumber());
        User saved = saveUser(user);

        return UserResponse.from(saved);
    }

    /**
     * DB 커넥션은 userQueryService.getByUsername() 조회 한 번만 점유한다.
     * 비밀번호 검증(BCrypt), JWT 발급, Redis 저장은 DB와 무관하므로 트랜잭션 밖에서 처리해
     * 부하 상황에서 커넥션 점유 시간을 최소화한다.
     */
    public LoginResponse login(LoginRequest request) {
        User user = userQueryService.getByUsername(request.username());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
        }
        if (user.isWithdrawn()) {
            throw new BusinessException(UserErrorCode.WITHDRAWN_USER);
        }

        return issueTokens(user);
    }

    /**
     * refresh token 로테이션: 사용된 토큰은 즉시 폐기하고 새 access/refresh token 쌍을 발급한다.
     * 이미 폐기된(예전) 토큰이 다시 들어오면 탈취로 간주해 해당 사용자의 세션을 강제 무효화한다.
     */
    /**
     * DB 커넥션은 userQueryService.getById() 조회 한 번만 점유한다.
     * 토큰 검증/생성과 Redis 로테이션은 DB와 무관하므로 트랜잭션 밖에서 처리해
     * 부하 상황에서 커넥션 점유 시간을 최소화한다.
     */
    public LoginResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        try {
            jwtProvider.validateToken(token);
        } catch (JwtException e) {
            log.debug("refresh token 검증 실패: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!jwtProvider.isRefreshToken(token)) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(token);
        String presentedTokenId = jwtProvider.getTokenId(token);
        String newTokenId = UUID.randomUUID().toString();

        RefreshTokenStore.RotateResult rotateResult = refreshTokenStore.compareAndRotate(userId, presentedTokenId, newTokenId);
        if (rotateResult == RefreshTokenStore.RotateResult.REUSE_DETECTED) {
            log.warn("탈취 의심: userId={}의 폐기된 refresh token이 재사용되어 세션을 강제 무효화했습니다.", userId);
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (rotateResult == RefreshTokenStore.RotateResult.NOT_FOUND) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userQueryService.getById(userId);
        if (user.isWithdrawn()) {
            refreshTokenStore.delete(userId);
            throw new BusinessException(UserErrorCode.WITHDRAWN_USER);
        }

        String accessToken = jwtProvider.createToken(user.getUserId(), user.getUsername(), user.getUserType().name());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUsername(), newTokenId);
        return new LoginResponse(accessToken, refreshToken);
    }

    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtProvider.createToken(user.getUserId(), user.getUsername(), user.getUserType().name());
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUsername(), tokenId);
        refreshTokenStore.save(user.getUserId(), tokenId);

        return new LoginResponse(accessToken, refreshToken);
    }

    /**
     * validateDuplicate()의 exists-then-save 사이엔 레이스가 있을 수 있어, 저장 시 유니크 제약 위반이 나면
     * 어느 필드가 충돌했는지 재조회해 기존 BusinessException으로 변환한다.
     */
    private User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByUsername(user.getUsername())) {
                throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
            }
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
            }
            if (user.getBusinessNumber() != null && userRepository.existsByBusinessNumber(user.getBusinessNumber())) {
                throw new BusinessException(UserErrorCode.DUPLICATE_BUSINESS_NUMBER);
            }
            throw e;
        }
    }

    private void validateDuplicate(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateDuplicateBusinessNumber(String businessNumber) {
        if (userRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
    }

    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new BusinessException(UserErrorCode.WITHDRAWN_USER);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        user.withdraw();
        refreshTokenStore.delete(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public void sendNotificationEmail(Long userId, EmailNotificationRequest request) {
        // 1. 유저 조회 (없으면 404 예외 올려라~)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        emailSender.send(user.getEmail(), request.subject(), request.body());

    }
}
