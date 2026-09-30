package com.programmers.kdt.user.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.presentation.dto.LoginRequest;
import com.programmers.kdt.user.presentation.dto.LoginResponse;
import com.programmers.kdt.user.presentation.dto.RefreshTokenRequest;
import com.programmers.kdt.user.presentation.dto.SignUpBusinessRequest;
import com.programmers.kdt.user.presentation.dto.SignUpIndividualRequest;
import com.programmers.kdt.user.presentation.dto.UserResponse;
import com.programmers.kdt.user.presentation.dto.WithdrawRequest;
import com.programmers.kdt.user.application.email.EmailVerificationService;
import com.programmers.kdt.user.domain.entity.Role;
import com.programmers.kdt.user.domain.entity.User;
import com.programmers.kdt.user.domain.entity.UserStatus;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import com.programmers.kdt.common.jwt.JwtProvider;
import com.programmers.kdt.user.infrastructure.jwt.RefreshTokenStore;
import com.programmers.kdt.user.infrastructure.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private UserService userService;

    @Test
    void 기업_회원가입_성공() {
        SignUpBusinessRequest request = new SignUpBusinessRequest(
                "business1", "business1@example.com", "password123",
                "회사이름", "123-45-67890");
        given(userRepository.existsByUsername(request.username())).willReturn(false);
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByBusinessNumber(request.businessNumber())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");

        User saved = User.signUpBusiness(request.username(), request.email(), "encodedPassword",
                request.businessName(), request.businessNumber());
        ReflectionTestUtils.setField(saved, "userId", 1L);
        given(userRepository.save(any(User.class))).willReturn(saved);

        UserResponse response = userService.signUpBusiness(request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("business1");
        assertThat(response.userType()).isEqualTo(Role.BUSINESS);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 중복된_사업자번호로_기업_회원가입시_예외() {
        SignUpBusinessRequest request = new SignUpBusinessRequest(
                "business1", "business1@example.com", "password123",
                "회사이름", "123-45-67890");
        given(userRepository.existsByUsername(request.username())).willReturn(false);
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByBusinessNumber(request.businessNumber())).willReturn(true);

        assertThatThrownBy(() -> userService.signUpBusiness(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.DUPLICATE_BUSINESS_NUMBER.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 회원가입_시_저장_직전_동시_가입으로_유니크_제약_위반되면_중복_예외로_변환() {
        SignUpIndividualRequest request = new SignUpIndividualRequest("user1", "user1@example.com", "password123");
        given(userRepository.existsByUsername(request.username())).willReturn(false, true);
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willThrow(new DataIntegrityViolationException("duplicate entry"));

        assertThatThrownBy(() -> userService.signUpIndividual(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.DUPLICATE_USERNAME.getMessage());
    }

    @Test
    void 이메일_인증_미완료_상태로_개인_회원가입시_예외() {
        SignUpIndividualRequest request = new SignUpIndividualRequest("user1", "user1@example.com", "password123");
        willThrow(new BusinessException(UserErrorCode.EMAIL_NOT_VERIFIED))
                .given(emailVerificationService).requireVerifiedEmail(request.email());

        assertThatThrownBy(() -> userService.signUpIndividual(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.EMAIL_NOT_VERIFIED.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 로그인_성공() {
        LoginRequest request = new LoginRequest("user1", "password123");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(userQueryService.getByUsername(request.username())).willReturn(user);
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
        given(jwtProvider.createToken(user.getUserId(), user.getUsername(), "INDIVIDUAL")).willReturn("issued-access-token");
        given(jwtProvider.createRefreshToken(any(), any(), anyString())).willReturn("issued-refresh-token");

        LoginResponse response = userService.login(request);

        assertThat(response.accessToken()).isEqualTo("issued-access-token");
        assertThat(response.refreshToken()).isEqualTo("issued-refresh-token");
        verify(refreshTokenStore).save(any(), anyString());
    }

    @Test
    void 사업자_회원_로그인시_토큰에_BUSINESS_role이_담긴다() {
        LoginRequest request = new LoginRequest("biz1", "password123");
        User user = User.signUpBusiness("biz1", "biz1@example.com", "encodedPassword", "회사이름", "123-45-67890");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(userQueryService.getByUsername(request.username())).willReturn(user);
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
        given(jwtProvider.createToken(user.getUserId(), user.getUsername(), "BUSINESS")).willReturn("issued-access-token");
        given(jwtProvider.createRefreshToken(any(), any(), anyString())).willReturn("issued-refresh-token");

        LoginResponse response = userService.login(request);

        assertThat(response.accessToken()).isEqualTo("issued-access-token");
        verify(jwtProvider).createToken(1L, "biz1", "BUSINESS");
    }

    @Test
    void 존재하지_않는_아이디로_로그인시_예외() {
        LoginRequest request = new LoginRequest("noSuchUser", "password123");
        given(userQueryService.getByUsername(request.username()))
                .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void 비밀번호_불일치로_로그인시_예외() {
        LoginRequest request = new LoginRequest("user1", "wrongPassword");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");

        given(userQueryService.getByUsername(request.username())).willReturn(user);
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_PASSWORD.getMessage());
    }

    @Test
    void 탈퇴한_계정으로_로그인시_예외() {
        LoginRequest request = new LoginRequest("user1", "password123");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

        given(userQueryService.getByUsername(request.username())).willReturn(user);
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.WITHDRAWN_USER.getMessage());
    }

    @Test
    void 회원_탈퇴_성공() {
        WithdrawRequest request = new WithdrawRequest("password123");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);

        userService.withdraw(1L, request);

        assertThat(user.isWithdrawn()).isTrue();
        verify(refreshTokenStore).delete(1L);
    }

    @Test
    void 존재하지_않는_유저_탈퇴시_예외() {
        WithdrawRequest request = new WithdrawRequest("password123");
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void 이미_탈퇴한_계정_재탈퇴시_예외() {
        WithdrawRequest request = new WithdrawRequest("password123");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.WITHDRAWN_USER.getMessage());
    }

    @Test
    void 비밀번호_불일치로_탈퇴시_예외() {
        WithdrawRequest request = new WithdrawRequest("wrongPassword");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> userService.withdraw(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_PASSWORD.getMessage());
    }

    @Test
    void 리프레시_토큰_갱신_성공_시_로테이션된다() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(jwtProvider.isRefreshToken(request.refreshToken())).willReturn(true);
        given(jwtProvider.getUserId(request.refreshToken())).willReturn(1L);
        given(jwtProvider.getTokenId(request.refreshToken())).willReturn("old-token-id");
        given(refreshTokenStore.compareAndRotate(eq(1L), eq("old-token-id"), anyString()))
                .willReturn(RefreshTokenStore.RotateResult.ROTATED);
        given(userQueryService.getById(1L)).willReturn(user);
        given(jwtProvider.createToken(1L, "user1", "INDIVIDUAL")).willReturn("new-access-token");
        given(jwtProvider.createRefreshToken(any(), any(), anyString())).willReturn("new-refresh-token");

        LoginResponse response = userService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenStore).compareAndRotate(eq(1L), eq("old-token-id"), anyString());
    }

    @Test
    void 만료되거나_위조된_리프레시_토큰이면_예외() {
        RefreshTokenRequest request = new RefreshTokenRequest("bad-token");
        willThrow(new JwtException("invalid")).given(jwtProvider).validateToken(request.refreshToken());

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    @Test
    void 액세스_토큰으로_리프레시_시도하면_예외() {
        RefreshTokenRequest request = new RefreshTokenRequest("access-token-not-refresh");
        given(jwtProvider.isRefreshToken(request.refreshToken())).willReturn(false);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    @Test
    void 저장된_토큰이_없으면_리프레시_예외() {
        RefreshTokenRequest request = new RefreshTokenRequest("token");
        given(jwtProvider.isRefreshToken(request.refreshToken())).willReturn(true);
        given(jwtProvider.getUserId(request.refreshToken())).willReturn(1L);
        given(refreshTokenStore.compareAndRotate(eq(1L), any(), anyString()))
                .willReturn(RefreshTokenStore.RotateResult.NOT_FOUND);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    @Test
    void 탈퇴한_유저의_리프레시_토큰이면_예외_및_세션삭제() {
        RefreshTokenRequest request = new RefreshTokenRequest("token");
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

        given(jwtProvider.isRefreshToken(request.refreshToken())).willReturn(true);
        given(jwtProvider.getUserId(request.refreshToken())).willReturn(1L);
        given(jwtProvider.getTokenId(request.refreshToken())).willReturn("token-id");
        given(refreshTokenStore.compareAndRotate(eq(1L), eq("token-id"), anyString()))
                .willReturn(RefreshTokenStore.RotateResult.ROTATED);
        given(userQueryService.getById(1L)).willReturn(user);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.WITHDRAWN_USER.getMessage());
        verify(refreshTokenStore).delete(1L);
    }

    @Test
    void 이미_폐기된_리프레시_토큰_재사용시_세션이_무효화된다() {
        RefreshTokenRequest request = new RefreshTokenRequest("reused-old-token");
        given(jwtProvider.isRefreshToken(request.refreshToken())).willReturn(true);
        given(jwtProvider.getUserId(request.refreshToken())).willReturn(1L);
        given(jwtProvider.getTokenId(request.refreshToken())).willReturn("stale-token-id");
        given(refreshTokenStore.compareAndRotate(eq(1L), eq("stale-token-id"), anyString()))
                .willReturn(RefreshTokenStore.RotateResult.REUSE_DETECTED);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    @Test
    void 로그아웃하면_저장된_리프레시_토큰이_삭제된다() {
        userService.logout(1L);

        verify(refreshTokenStore).delete(1L);
    }

    @Test
    void 회원_조회_성공() {
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getUser(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("user1");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.userType()).isEqualTo(Role.INDIVIDUAL);
    }

    @Test
    void 존재하지_않는_유저_조회시_예외() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void 탈퇴한_유저도_조회는_정상_응답() {
        User user = User.signUpIndividual("user1", "user1@example.com", "encodedPassword");
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getUser(1L);

        assertThat(response.status()).isEqualTo(UserStatus.WITHDRAWN);
    }

}
