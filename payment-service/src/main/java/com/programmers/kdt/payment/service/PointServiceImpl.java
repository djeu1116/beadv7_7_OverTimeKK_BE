package com.programmers.kdt.payment.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.dto.GetPointBalanceResponse;
import com.programmers.kdt.payment.dto.GetPointHistoryResponse;
import com.programmers.kdt.payment.entity.Point;
import com.programmers.kdt.payment.entity.PointLog;
import com.programmers.kdt.payment.entity.PointType;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.repository.PointLogRepository;
import com.programmers.kdt.payment.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final PointRepository pointRepository;
    private final PointLogRepository pointLogRepository;


    @Override
    @Transactional
    public void usePoint(Long userId, Long amount, String eventId) {
        if (pointLogRepository.findByEventId(eventId).isPresent()) {
            log.warn("이미 처리된 포인트 사용 이벤트 - eventId:{}", eventId);
            return;
        }

        Point point = pointRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.POINT_NOT_FOUND, userId));

        try {
            point.use(amount);
            pointRepository.saveAndFlush(point); // 동시 차감으로 인한 잔액 불일치 방지
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION);
        }

        pointLogRepository.save(PointLog.use(userId, amount, eventId));
    }

    @Override
    @Transactional
    public void earnPoint(Long userId, Long amount, String eventId) {
        if (pointLogRepository.findByEventId(eventId).isPresent()) {
            log.warn("이미 처리된 포인트 적립 이벤트 - eventId:{}", eventId);
            return;
        }

        Point point = pointRepository.findById(userId)
                .orElseGet(() -> Point.create(userId));

        try {
            point.earn(amount);
            pointRepository.saveAndFlush(point);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION);
        }
        pointLogRepository.save(PointLog.earn(userId, amount, eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public Long findUsedAmount(String eventId) {
        return pointLogRepository.findByEventId(eventId)
                .map(PointLog::getAmount)
                .orElse(0L);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackPoint(String originEventId, Long amount, String rollbackEventId, boolean isFullRollback) {
        if (amount == null || amount <= 0) {
            return;
        }

        if (pointLogRepository.findByEventId(rollbackEventId).isPresent()) {
            log.warn("이미 처리된 포인트 환급 이벤트 - eventId:{}", rollbackEventId);
            return; // 중복 호출 방지
        }

        PointLog originLog = pointLogRepository.findByEventId(originEventId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.ORIGIN_POINT_LOG_NOT_FOUND, originEventId));

        Point point = pointRepository.findById(originLog.getUserId())
                .orElseThrow(() -> new BusinessException(PointErrorCode.POINT_NOT_FOUND, originLog.getUserId()));

        try {
            point.rollbackUse(amount);
            pointRepository.saveAndFlush(point);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION);
        }

        pointLogRepository.save(PointLog.rollback(originLog, amount, rollbackEventId, isFullRollback));
    }

    @Override
    @Transactional(readOnly = true)
    public GetPointBalanceResponse getBalance(Long userId) {
        Long balance = pointRepository.findById(userId)
                .map(Point::getTotalPoint)
                .orElse(0L);
        return GetPointBalanceResponse.of(userId, balance);

    }

    @Override
    @Transactional(readOnly = true)
    public Page<GetPointHistoryResponse> getPointLogHistory(Long userId, Pageable pageable) {
        List<PointLog> logsAsc = pointLogRepository.findByUserIdOrderByCreatedAtAsc(userId);

        List<GetPointHistoryResponse> responses = new ArrayList<>(logsAsc.size());
        long runningBalance = 0L;

        for (PointLog log : logsAsc) {
            long signedAmount = isCredit(log.getPointType()) ? log.getAmount() : -log.getAmount();
            runningBalance += signedAmount;
            responses.add(GetPointHistoryResponse.of(
                    log.getId(), log.getCreatedAt(), log.getPointType(), signedAmount, runningBalance
            ));
        }

        Collections.reverse(responses); // 최신순으로 정렬

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), responses.size());

        List<GetPointHistoryResponse> pageContent = start >= responses.size()
                ? List.of()
                : responses.subList(start, end);

        return new PageImpl<>(pageContent, pageable, responses.size());
    }

    private boolean isCredit(PointType type) {
        return type == PointType.EARN || type == PointType.CANCELLED || type == PointType.PARTIAL_CANCELLED;
    }

}


