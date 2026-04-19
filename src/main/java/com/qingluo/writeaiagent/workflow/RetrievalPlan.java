package com.qingluo.writeaiagent.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 检索计划对象
 * 定义如何执行检索的完整计划
 */
@Data
@Builder
public class RetrievalPlan {

    /**
     * 检索策略类型
     */
    private RetrievalStrategy strategy;

    /**
     * 要检索的上下文类型列表
     */
    private List<TaskConstraints.ContextType> contextTypes;

    /**
     * 每个上下文类型的检索配置
     */
    private List<RetrievalConfig> retrievalConfigs;

    /**
     * 召回数量上限
     */
    private int topK;

    /**
     * 是否启用重排序
     */
    private boolean enableReranking;

    /**
     * 是否启用去重
     */
    private boolean enableDeduplication;

    /**
     * 相邻Chunk拼接数量
     */
    private int adjacentChunkMergeCount;

    /**
     * 元数据过滤器
     */
    private MetadataFilter metadataFilter;

    /**
     * 检索策略枚举
     */
    public enum RetrievalStrategy {
        BROAD("broad", "广度优先 - 多类型检索"),
        FOCUSED("focused", "聚焦检索 - 单一类型深度检索"),
        HYBRID("hybrid", "混合检索 - 结合向量和关键词"),
        ADAPTIVE("adaptive", "自适应检索 - 根据结果动态调整");

        private final String code;
        private final String description;

        RetrievalStrategy(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 单个检索配置
     */
    @Data
    @Builder
    public static class RetrievalConfig {
        private TaskConstraints.ContextType contextType;
        private String searchQuery;
        private int topK;
        private double minSimilarityScore;
        private String[] metadataFilters;
        private int priority;

        public static RetrievalConfig of(TaskConstraints.ContextType type, String query) {
            return RetrievalConfig.builder()
                    .contextType(type)
                    .searchQuery(query)
                    .topK(5)
                    .minSimilarityScore(0.6)
                    .priority(1)
                    .build();
        }
    }

    /**
     * 元数据过滤器
     */
    @Data
    @Builder
    public static class MetadataFilter {
        private String chapterId;
        private String characterId;
        private String plotlineId;
        private String timeRange;
        private String[] tags;
        private String[] excludeTags;

        public static MetadataFilter none() {
            return MetadataFilter.builder().build();
        }

        public boolean hasAnyFilter() {
            return (chapterId != null && !chapterId.isBlank()) ||
                   (characterId != null && !characterId.isBlank()) ||
                   (plotlineId != null && !plotlineId.isBlank()) ||
                   (timeRange != null && !timeRange.isBlank()) ||
                   (tags != null && tags.length > 0) ||
                   (excludeTags != null && excludeTags.length > 0);
        }
    }

    /**
     * 创建默认检索计划
     */
    public static RetrievalPlan defaultPlan(List<TaskConstraints.ContextType> contextTypes) {
        return RetrievalPlan.builder()
                .strategy(RetrievalStrategy.HYBRID)
                .contextTypes(contextTypes)
                .topK(10)
                .enableReranking(true)
                .enableDeduplication(true)
                .adjacentChunkMergeCount(2)
                .metadataFilter(MetadataFilter.none())
                .build();
    }
}