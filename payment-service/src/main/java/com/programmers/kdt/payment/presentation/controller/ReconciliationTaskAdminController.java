package com.programmers.kdt.payment.presentation.controller;

import com.programmers.kdt.common.reconciliation.ReconciliationTask;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskRepository;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskStatus;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

// 로그 grep 대신 미해결 대사 건을 조회/정리하는 창구. 현재는 관리자 권한 체크 없이 열려있음
// (PointEarnAdminController와 같은 상태) -> 추후 내부/관리자 인증 추가해야함.
@RestController
@RequestMapping("/api/admin/reconciliation-tasks")
@RequiredArgsConstructor
public class ReconciliationTaskAdminController {

    private final ReconciliationTaskRepository reconciliationTaskRepository;

    @GetMapping
    public ApiResponse<Page<ReconciliationTask>> list(
            @RequestParam(required = false) ReconciliationTaskType type,
            Pageable pageable
    ) {
        Page<ReconciliationTask> tasks = type == null
                ? reconciliationTaskRepository.findByStatus(ReconciliationTaskStatus.OPEN, pageable)
                : reconciliationTaskRepository.findByStatusAndTaskType(ReconciliationTaskStatus.OPEN, type, pageable);
        return ApiResponse.success(tasks);
    }

    // 수동으로 확인·조치한 건을 닫는다. 실제 보정 작업(포인트 재환급 등)은 이 API가 하지 않는다 - 상태만 남긴다.
    @PostMapping("/{taskId}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long taskId) {
        Optional.ofNullable(reconciliationTaskRepository.findById(taskId).orElse(null))
                .ifPresent(task -> {
                    task.resolve();
                    reconciliationTaskRepository.save(task);
                });
        return ApiResponse.success(null);
    }
}
