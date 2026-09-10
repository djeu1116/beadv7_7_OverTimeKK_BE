package com.programmers.kdt.performance.event;

import com.programmers.kdt.search.PerformanceDocument;
import com.programmers.kdt.search.PerformanceSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceDocumentSaveEventListener {

    // no-es 프로필에서는 Elasticsearch 자동설정이 꺼져서 빈 자체가 없음 - Optional로 받아서 스킵 처리
    private final Optional<PerformanceSearchRepository> performanceSearchRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleDocumentSave(PerformanceDocumentSaveEvent event) {
        if (performanceSearchRepository.isEmpty()) {
            log.debug("Elasticsearch 비활성화 상태 - 검색 인덱스 저장 스킵 (performanceId={})", event.performance().getPerformanceId());
            return;
        }
        performanceSearchRepository.get().save(PerformanceDocument.from(event.performance()));
    }
}
