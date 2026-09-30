package com.programmers.kdt.performance.infrastructure.search;

import com.programmers.kdt.performance.domain.entity.Performance;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "performances")
@Setting(settingPath = "elasticsearch/performance-settings.json")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceDocument {
    @Id
    @Field(type = FieldType.Long)
    private Long performanceId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search"),
            otherFields = {
                    @InnerField(suffix="auto", type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
            }
    )
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
    private String description;

    private PerformanceDocument(Long performanceId, String title, String description) {
        this.performanceId = performanceId;
        this.title = title;
        this.description = description;
    }

    public static PerformanceDocument from(Performance performance) {
        return new PerformanceDocument(
                performance.getPerformanceId(),
                performance.getTitle(),
                performance.getDescription()
        );
    }
}
