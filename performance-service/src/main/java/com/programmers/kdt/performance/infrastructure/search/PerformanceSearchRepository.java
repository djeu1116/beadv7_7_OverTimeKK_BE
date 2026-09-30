package com.programmers.kdt.performance.infrastructure.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface PerformanceSearchRepository extends ElasticsearchRepository<PerformanceDocument, Long> {

    List<PerformanceDocument> findTop8ByTitle(String title);
}
