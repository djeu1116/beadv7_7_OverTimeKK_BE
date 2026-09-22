package com.programmers.kdt.common.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 자동 재시도가 소진된 지점에서 호출한다. 실패 처리 자체의 트랜잭션과 무관하게 독립적으로 커밋되어야
// 하므로(호출자가 예외를 던지고 롤백하더라도 대사 기록은 남아야 함) REQUIRES_NEW로 고정한다.
@Component
@RequiredArgsConstructor
public class ReconciliationTaskWriter {

    private final ReconciliationTaskRepository reconciliationTaskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ReconciliationTaskType taskType, Long aggregateId, Long amount, String detail) {
        reconciliationTaskRepository.save(ReconciliationTask.create(taskType, aggregateId, amount, detail));
    }

    public void record(ReconciliationTaskType taskType, Long aggregateId, String detail) {
        record(taskType, aggregateId, null, detail);
    }
}
