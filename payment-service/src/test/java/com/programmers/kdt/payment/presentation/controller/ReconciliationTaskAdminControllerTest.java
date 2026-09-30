package com.programmers.kdt.payment.presentation.controller;

import com.programmers.kdt.common.reconciliation.ReconciliationTask;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskRepository;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskStatus;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationTaskAdminControllerTest {

    @Mock
    private ReconciliationTaskRepository reconciliationTaskRepository;

    private ReconciliationTaskAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ReconciliationTaskAdminController(reconciliationTaskRepository);
    }

    @Test
    @DisplayName("타입을 지정하지 않으면 OPEN 전체를 조회한다.")
    void listWithoutType() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReconciliationTask> page = new PageImpl<>(java.util.List.of());
        when(reconciliationTaskRepository.findByStatus(ReconciliationTaskStatus.OPEN, pageable)).thenReturn(page);

        var response = controller.list(null, pageable);

        assertThat(response.success()).isTrue();
        verify(reconciliationTaskRepository, never()).findByStatusAndTaskType(any(), any(), any());
    }

    @Test
    @DisplayName("타입을 지정하면 OPEN + 해당 타입으로 좁혀서 조회한다.")
    void listWithType() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReconciliationTask> page = new PageImpl<>(java.util.List.of());
        when(reconciliationTaskRepository.findByStatusAndTaskType(
                ReconciliationTaskStatus.OPEN, ReconciliationTaskType.COMPENSATION_FAILED, pageable)).thenReturn(page);

        controller.list(ReconciliationTaskType.COMPENSATION_FAILED, pageable);

        verify(reconciliationTaskRepository).findByStatusAndTaskType(ReconciliationTaskStatus.OPEN, ReconciliationTaskType.COMPENSATION_FAILED, pageable);
    }

    @Test
    @DisplayName("resolve하면 해당 건을 저장한다.")
    void resolve() {
        ReconciliationTask task = ReconciliationTask.create(ReconciliationTaskType.COMPENSATION_FAILED, 1L, null, null);
        when(reconciliationTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        controller.resolve(1L);

        assertThat(task.getStatus()).isEqualTo(ReconciliationTaskStatus.RESOLVED);
        verify(reconciliationTaskRepository).save(task);
    }

    @Test
    @DisplayName("없는 id면 아무 일도 하지 않는다.")
    void resolveNotFound() {
        when(reconciliationTaskRepository.findById(999L)).thenReturn(Optional.empty());

        controller.resolve(999L);

        verify(reconciliationTaskRepository, never()).save(any());
    }
}
