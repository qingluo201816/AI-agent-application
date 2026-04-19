package com.qingluo.writeaiagent.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 评估结果对象
 * 包含硬约束检查、软质量检查和问题分类的结果
 */
@Data
@Builder
public class EvaluationResult {

    /**
     * 是否通过评估
     */
    private boolean passed;

    /**
     * 问题类型分类
     */
    private IssueType issueType;

    /**
     * 具体问题列表
     */
    @Builder.Default
    private List<Problem> problems = new ArrayList<>();

    /**
     * 建议的下一阶段
     */
    private WorkflowStage suggestedNextStage;

    /**
     * 评估置信度 (0.0 - 1.0)
     */
    private double confidence;

    /**
     * 详细评估报告
     */
    private String evaluationReport;

    /**
     * 缺失的上下文类型
     */
    @Builder.Default
    private List<String> missingContextTypes = new ArrayList<>();

    /**
     * 建议的检索调整
     */
    private SuggestedRetrievalAdjustments suggestedRetrievalAdjustments;

    /**
     * 建议的生成修复
     */
    private SuggestedGenerationFixes suggestedGenerationFixes;

    /**
     * 问题类型枚举
     */
    public enum IssueType {
        /**
         * 信息层问题 - 需要回跳到检索阶段
         * 例如：上下文不足、召回偏差、缺少设定信息
         */
        INFORMATION("information", "信息层问题", WorkflowStage.RETRIEVE),

        /**
         * 表达层问题 - 需要回跳到生成阶段
         * 例如：文风不对、视角错误、篇幅不足、表达不自然
         */
        EXPRESSION("expression", "表达层问题", WorkflowStage.GENERATE),

        /**
         * 交付层问题 - 需要回跳到交付阶段
         * 例如：PDF生成失败、文件格式问题
         */
        DELIVERY("delivery", "交付层问题", WorkflowStage.DELIVER),

        /**
         * 无问题 - 评估通过
         */
        NONE("none", "无问题", WorkflowStage.DELIVER);

        private final String code;
        private final String description;
        private final WorkflowStage fallbackStage;

        IssueType(String code, String description, WorkflowStage fallbackStage) {
            this.code = code;
            this.description = description;
            this.fallbackStage = fallbackStage;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public WorkflowStage getFallbackStage() {
            return fallbackStage;
        }

        public static IssueType fromCode(String code) {
            for (IssueType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    /**
     * 单个问题描述
     */
    @Data
    @Builder
    public static class Problem {
        private ProblemCategory category;
        private ProblemSeverity severity;
        private String description;
        private String location;
        private String suggestion;

        public enum ProblemCategory {
            WORD_COUNT("字数问题"),
            POV_VIOLATION("视角问题"),
            STYLE_MISMATCH("风格不一致"),
            PLOT_INCONSISTENCY("情节不一致"),
            LOGIC_ERROR("逻辑错误"),
            FACTUAL_ERROR("事实错误"),
            TIMELINE_CONFLICT("时间线冲突"),
            CHARACTER_INCONSISTENCY("人物设定不一致"),
            TONE_MISMATCH("氛围/基调不符"),
            MISSING_CONTENT("缺少必要内容"),
            REPETITION("重复问题"),
            PACING_ISSUE("节奏问题"),
            PDF_GENERATION_FAILED("PDF生成失败"),
            FILE_FORMAT_ERROR("文件格式错误"),
            UNEXPECTED_SPOILER("提前暴露信息"),
            UNRESOLVED_CLIFFHANGER("悬念未处理");

            private final String description;

            ProblemCategory(String description) {
                this.description = description;
            }

            public String getDescription() {
                return description;
            }
        }

        public enum ProblemSeverity {
            CRITICAL("严重", 3),
            MAJOR("重要", 2),
            MINOR("轻微", 1);

            private final String description;
            private final int weight;

            ProblemSeverity(String description, int weight) {
                this.description = description;
                this.weight = weight;
            }

            public String getDescription() {
                return description;
            }

            public int getWeight() {
                return weight;
            }
        }
    }

    /**
     * 创建通过评估的结果
     */
    public static EvaluationResult pass() {
        return EvaluationResult.builder()
                .passed(true)
                .issueType(IssueType.NONE)
                .confidence(1.0)
                .evaluationReport("内容通过所有评估检查")
                .suggestedNextStage(WorkflowStage.DELIVER)
                .problems(new ArrayList<>())
                .build();
    }

    /**
     * 创建未通过评估的结果
     */
    public static EvaluationResult fail(IssueType issueType, List<Problem> problems, String report) {
        return EvaluationResult.builder()
                .passed(false)
                .issueType(issueType)
                .confidence(0.5)
                .evaluationReport(report)
                .suggestedNextStage(issueType.getFallbackStage())
                .problems(problems)
                .missingContextTypes(new ArrayList<>())
                .suggestedRetrievalAdjustments(null)
                .suggestedGenerationFixes(null)
                .build();
    }

    /**
     * 创建失败的评估结果（带详细调整建议）
     */
    public static EvaluationResult fail(IssueType issueType, List<Problem> problems, String report, 
                                      List<String> missingContextTypes, 
                                      SuggestedRetrievalAdjustments suggestedRetrievalAdjustments, 
                                      SuggestedGenerationFixes suggestedGenerationFixes) {
        return EvaluationResult.builder()
                .passed(false)
                .issueType(issueType)
                .confidence(0.5)
                .evaluationReport(report)
                .suggestedNextStage(issueType.getFallbackStage())
                .problems(problems)
                .missingContextTypes(missingContextTypes != null ? missingContextTypes : new ArrayList<>())
                .suggestedRetrievalAdjustments(suggestedRetrievalAdjustments)
                .suggestedGenerationFixes(suggestedGenerationFixes)
                .build();
    }

    /**
     * 检查是否有严重问题
     */
    public boolean hasCriticalProblems() {
        return problems.stream()
                .anyMatch(p -> p.getSeverity() == Problem.ProblemSeverity.CRITICAL);
    }

    /**
     * 获取问题摘要
     */
    public String getProblemSummary() {
        if (problems.isEmpty()) {
            return "无问题";
        }
        long critical = problems.stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.CRITICAL).count();
        long major = problems.stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.MAJOR).count();
        long minor = problems.stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.MINOR).count();
        return String.format("严重:%d, 重要:%d, 轻微:%d", critical, major, minor);
    }

    /**
     * 建议的检索调整
     */
    @Data
    @Builder
    public static class SuggestedRetrievalAdjustments {
        /**
         * 是否扩大章节范围
         */
        private boolean expandChapterRange;

        /**
         * 是否放宽 metadata filter
         */
        private boolean relaxMetadataFilter;

        /**
         * 是否增加 topK
         */
        private Integer increaseTopK;

        /**
         * 建议增加的上下文类型
         */
        private List<String> suggestedAdditionalContextTypes;

        /**
         * 建议的查询关键词
         */
        private List<String> suggestedQueryKeywords;

        /**
         * 建议的检索策略
         */
        private String suggestedRetrievalStrategy;
    }

    /**
     * 建议的生成修复
     */
    @Data
    @Builder
    public static class SuggestedGenerationFixes {
        /**
         * 是否需要局部改写
         */
        private boolean needLocalRewrite;

        /**
         * 改写范围
         */
        private String rewriteScope;

        /**
         * 具体修复方向
         */
        private List<String> fixDirections;

        /**
         * 风格调整建议
         */
        private String styleAdjustment;

        /**
         * 视角调整建议
         */
        private String povAdjustment;

        /**
         * 长度调整建议
         */
        private String lengthAdjustment;
    }
}