package com.programmers.kdt.user.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.domain.entity.User;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import com.programmers.kdt.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 조회만 담당하는 트랜잭션 경계. login/refresh처럼 DB 조회 뒤에
 * 비밀번호 검증, JWT 발급, Redis 호출 등 DB와 무관한 작업이 이어지는 흐름에서
 * 커넥션 점유 시간을 조회 한 번으로 제한하기 위해 별도 빈으로 분리했다.
 * (같은 클래스 내 self-invocation은 프록시를 안 타서 @Transactional이 무시되므로 분리가 필요함)
 */
@Service
@RequiredArgsConstructor
class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
