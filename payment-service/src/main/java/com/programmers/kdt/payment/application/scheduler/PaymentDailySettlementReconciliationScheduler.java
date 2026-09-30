package com.programmers.kdt.payment.application.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.infrastructure.client.pg.PgClient;
import com.programmers.kdt.payment.infrastructure.client.pg.PgClientException;
import com.programmers.kdt.payment.infrastructure.client.pg.PgResponseMismatchException;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentAttempt;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// PaymentReconciliationScheduler(분 단위, CONFIRM_PENDING_VERIFICATION 대상)와는 별개로,
// 이미 종결(PAID/FAILED/CANCELLED)된 최근 결제가 지금도 PG쪽과 일치하는지 하루 한 번 재검증한다.
// 목적: 확정 이후 어딘가에서(이중 취소, 대사 안 된 크래시, 수동 조작 등) DB와 PG가 어긋난 걸 뒤늦게라도 잡아내는 안전망.
// Toss 거래내역 일괄조회 API는 쓰지 않는다 - 우리 DB에 이미 있는 결제 각각을 select()로 재조회해서 비교하는 방식이라
// "우리가 아예 모르는 PG쪽 거래"는 못 잡지만, 새 PG API 없이 지금 인터페이스만으로 실측 검증이 가능하다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDailySettlementReconciliationScheduler {

    private static final int BATCH_SIZE = 200;
    private static final Duration LOOKBACK = Duration.ofDays(1);
    private static final List<PaymentStatus> TERMINAL_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PgClient pgClient;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(cron = "0 0 4 * * *")
    public void reconcileDailySettlement() {
        try {
            run();
        } catch (Exception e) {
            log.error("[DAILY_SETTLEMENT_BATCH_FAILED] 일일 PG 대사 배치 자체가 실패. 자동 재처리 되지 않으니 수동 확인 필요.", e);
            // 배치 단위 실패라 특정 결제 하나로 좁혀지지 않음 - aggregateId는 관례상 0
            reconciliationTaskWriter.record(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH, 0L,
                    "batch failure: " + e.getMessage());
        }
    }

    private void run() {
        LocalDateTime since = LocalDateTime.now().minus(LOOKBACK);
        int checked = 0;
        int mismatched = 0;
        int page = 0;
        Page<Payment> result;
        do {
            result = paymentRepository.findByPaymentStatusInAndModifiedAtAfter(
                    TERMINAL_STATUSES, since, PageRequest.of(page, BATCH_SIZE, Sort.by("id").ascending()));
            for (Payment payment : result) {
                checked++;
                try {
                    if (checkMismatch(payment)) mismatched++;
                } catch (Exception e) {
                    log.error("일일 PG 대사 중 결제 1건 처리 실패 - paymentId={}", payment.getId(), e);
                }
            }
            page++;
        } while (result.hasNext());

        log.info("일일 PG 대사 완료 - 검사 {}건, 불일치 {}건", checked, mismatched);
    }

    private boolean checkMismatch(Payment payment) {
        if (payment.getPaymentKey() == null) {
            return false; // 승인 단계까지 못 간 결제(READY에서 FAILED)는 PG에 남은 게 없음
        }

        // select()가 내부적으로 orderId/금액까지 검증하므로(M-1) 기대 금액이 필요하다 - attempt에 그
        // 시도가 실제로 쓴 포인트가 남아있어 그걸로 PG가 받았어야 할 금액을 구한다.
        Long expectedAmount = expectedPgAmount(payment);
        if (expectedAmount == null) {
            log.warn("일일 PG 대사 중 attempt 이력이 없어 기대 금액을 못 구함, 건너뜀 - paymentId={}", payment.getId());
            return false;
        }

        boolean expectedCharged = payment.getPaymentStatus() == PaymentStatus.PAID;
        boolean actuallyCharged;
        try {
            actuallyCharged = pgClient.select(payment.getPaymentKey(), payment.getPgOrderId(), expectedAmount).success();
        } catch (PgClientException | RestClientException e) {
            // 조회 자체가 안 되면 이번 배치에서는 판단할 근거가 없으니 건너뛴다 - 내일 다시 대상에 포함됨
            log.warn("일일 PG 대사 중 재조회 실패, 이번 배치는 건너뜀 - paymentId={}", payment.getId(), e);
            return false;
        } catch (PgResponseMismatchException e) {
            // PG가 "성공(충전됨)"이라 답했는데 orderId/금액이 다름 - 그 자체가 곧 불일치
            recordMismatch(payment, "PG 응답 자체가 불일치: " + e.getMessage());
            return true;
        }

        if (expectedCharged == actuallyCharged) {
            return false;
        }

        recordMismatch(payment, "ourStatus=" + payment.getPaymentStatus() + ", pgCharged=" + actuallyCharged);
        return true;
    }

    private Long expectedPgAmount(Payment payment) {
        Optional<PaymentAttempt> attempt = paymentAttemptRepository.findByPaymentIdAndAttemptSeq(
                payment.getId(), payment.getAttemptSeq());
        return attempt.map(a -> payment.getAmount() - a.getUsedPoint()).orElse(null);
    }

    private void recordMismatch(Payment payment, String detail) {
        log.error("[DAILY_SETTLEMENT_MISMATCH] DB와 PG 상태 불일치 - paymentId={}, orderId={}, {}",
                payment.getId(), payment.getOrderId(), detail);
        reconciliationTaskWriter.record(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH, payment.getId(),
                "orderId=" + payment.getOrderId() + ", " + detail);
    }
}
