package com.programmers.kdt.common.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationTaskRepository extends JpaRepository<ReconciliationTask, Long> {
    Page<ReconciliationTask> findByStatus(ReconciliationTaskStatus status, Pageable pageable);
    Page<ReconciliationTask> findByStatusAndTaskType(ReconciliationTaskStatus status, ReconciliationTaskType taskType, Pageable pageable);
}
