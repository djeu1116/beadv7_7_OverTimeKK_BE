package com.programmers.kdt.payment.service;

import com.programmers.kdt.payment.dto.GetPointBalanceResponse;
import com.programmers.kdt.payment.dto.GetPointHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface PointService {

    // 포인트 사용 (결제 시점에 호출)
    void usePoint(Long userId, Long amount, String eventId);

    // 포인트 적립 (공연 종료 배치에서 호출)
    void earnPoint(Long userId, Long amount, String eventId);

    // eventId로 남겨진 포인트 사용/환급 금액을 조회
    Long findUsedAmount(String eventId);

    // 포인트 환급(결제 실패/환불 완료 시점에 호출)
    void rollbackPoint(String originEventId, Long amount, String rollbackEventId, boolean isFullRollback);

    // 사용자 포인트 잔액 조회
    GetPointBalanceResponse getBalance(Long userId);

    // 포인트 적립 / 사용 내역 조회
    Page<GetPointHistoryResponse> getPointLogHistory(Long userId, Pageable pageable);
}
