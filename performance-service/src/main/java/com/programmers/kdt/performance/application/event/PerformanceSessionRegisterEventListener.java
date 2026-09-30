package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceSessionRegisterEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PerformanceSessionRegisterEventListener {

    private final PerformanceSessionRepository sessionRepository;

    @EventListener
    public void handleSessionRegister(PerformanceSessionRegisterEvent event) {
        if (event.sessionRequests().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "회차정보");
        }

        List<PerformanceSession> sessions = event.sessionRequests().stream()
                .map(session -> session.toSession(event.performance()))
                .toList();
        sessionRepository.saveAll(sessions);
    }
}