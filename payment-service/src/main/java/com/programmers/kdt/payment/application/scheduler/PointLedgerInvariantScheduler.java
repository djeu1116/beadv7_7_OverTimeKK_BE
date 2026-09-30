package com.programmers.kdt.payment.application.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.domain.entity.PointLedger;
import com.programmers.kdt.payment.domain.entity.PointLog;
import com.programmers.kdt.payment.domain.entity.PointType;
import com.programmers.kdt.payment.infrastructure.repository.PointLedgerRepository;
import com.programmers.kdt.payment.infrastructure.repository.PointLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// point_ledger(원장, 환급 한도 관리)와 point_log(사용/환급 감사로그)를 하루 한 번 서로 대조한다.
// 원장 자체는 PointLedger.consume()을 거치는 한 항상 정확하다 - 문제는 그 경로를 거치지 않고
// point_log에 CANCELLED/PARTIAL_CANCELLED 행이 생기는 경우(버그, 수동 DB 조작 등)인데, 그때도
// 원장 혼자 봐서는 못 잡는다. 그래서 두 테이블을 직접 맞대본다. 두 가지를 검사한다:
// 1) 정합성 - 원장이 암시하는 "이미 환급된 금액"(usedAmount - remainingRefundable)과
//    실제 point_log에 기록된 환급 합계가 같은지
// 2) 완결성 - 사용(USE) 로그마다 원장 행이 실제로 존재하는지(원장 생성 누락 버그)
// 불일치를 발견해도 자동 교정은 하지 않는다 - 다른 대사 배치들과 같은 판단(돈 관련 데이터의 자동 보정은
// 그 자체로 위험, 기록만 하고 사람이 확인).
@Slf4j
@Component
@RequiredArgsConstructor
public class PointLedgerInvariantScheduler {

    private static final int BATCH_SIZE = 200;
    private static final List<PointType> ROLLBACK_TYPES = List.of(PointType.CANCELLED, PointType.PARTIAL_CANCELLED);

    private final PointLedgerRepository pointLedgerRepository;
    private final PointLogRepository pointLogRepository;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(cron = "0 30 4 * * *")
    public void verifyInvariants() {
        try {
            run();
        } catch (Exception e) {
            log.error("[POINT_LEDGER_INVARIANT_BATCH_FAILED] 포인트 원장 불변식 검증 배치 자체가 실패. 자동 재처리 되지 않으니 수동 확인 필요.", e);
            // 배치 단위 실패라 특정 원장 하나로 좁혀지지 않음 - aggregateId는 관례상 0
            reconciliationTaskWriter.record(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH, 0L,
                    "batch failure: " + e.getMessage());
        }
    }

    private void run() {
        int consistencyChecked = 0;
        int consistencyMismatched = 0;
        int page = 0;
        Page<PointLedger> ledgerPage;
        do {
            ledgerPage = pointLedgerRepository.findAll(PageRequest.of(page, BATCH_SIZE, Sort.by("id").ascending()));
            for (PointLedger ledger : ledgerPage) {
                consistencyChecked++;
                try {
                    if (checkConsistency(ledger)) consistencyMismatched++;
                } catch (Exception e) {
                    log.error("포인트 원장 정합성 검증 중 결제 1건 처리 실패 - ledgerId={}", ledger.getId(), e);
                }
            }
            page++;
        } while (ledgerPage.hasNext());

        int completenessChecked = 0;
        int completenessMismatched = 0;
        page = 0;
        Page<PointLog> useLogPage;
        do {
            useLogPage = pointLogRepository.findByPointType(PointType.USE, PageRequest.of(page, BATCH_SIZE, Sort.by("id").ascending()));
            for (PointLog useLog : useLogPage) {
                completenessChecked++;
                try {
                    if (checkCompleteness(useLog)) completenessMismatched++;
                } catch (Exception e) {
                    log.error("포인트 원장 완결성 검증 중 사용 로그 1건 처리 실패 - pointLogId={}", useLog.getId(), e);
                }
            }
            page++;
        } while (useLogPage.hasNext());

        log.info("포인트 원장 불변식 검증 완료 - 정합성 {}건 중 {}건 불일치, 완결성 {}건 중 {}건 불일치",
                consistencyChecked, consistencyMismatched, completenessChecked, completenessMismatched);
    }

    private boolean checkConsistency(PointLedger ledger) {
        Long expectedRolledBack = ledger.getUsedAmount() - ledger.getRemainingRefundable();
        Long actualRolledBack = pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(ledger.getUseLogId(), ROLLBACK_TYPES);

        if (expectedRolledBack.equals(actualRolledBack)) {
            return false;
        }

        String detail = "useLogId=" + ledger.getUseLogId() + ", usedAmount=" + ledger.getUsedAmount()
                + ", remainingRefundable=" + ledger.getRemainingRefundable()
                + ", expectedRolledBack=" + expectedRolledBack + ", actualRolledBack=" + actualRolledBack;
        log.error("[POINT_LEDGER_INVARIANT_MISMATCH] 원장-로그 환급 합계 불일치 - ledgerId={}, {}", ledger.getId(), detail);
        reconciliationTaskWriter.record(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH, ledger.getId(), detail);
        return true;
    }

    private boolean checkCompleteness(PointLog useLog) {
        if (pointLedgerRepository.findByUseLogId(useLog.getId()).isPresent()) {
            return false;
        }

        String detail = "useLogId=" + useLog.getId() + ", userId=" + useLog.getUserId()
                + ", amount=" + useLog.getAmount() + ", 이 사용 로그에 대한 원장 행이 없음";
        log.error("[POINT_LEDGER_INVARIANT_MISMATCH] 원장 행 누락 - {}", detail);
        reconciliationTaskWriter.record(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH, useLog.getId(), detail);
        return true;
    }
}
