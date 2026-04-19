package com.qingluo.writeaiagent.workflow;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一任务状态对象
 * 贯穿整个工作流程的核心上下文对象
 */
@Data
@Builder
@Slf4j
public class TaskContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识
     */
    private String taskId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 任务类型
     */
    private TaskConstraints.TaskType taskType;

    /**
     * 当前工作流阶段
     */
    private WorkflowStage currentStage;

    /**
     * 任务状态
     */
    @Builder.Default
    private TaskStatus status = TaskStatus.CREATED;

    /**
     * 结构化约束
     */
    private TaskConstraints constraints;

    /**
     * 上下文类型列表（从约束中提取）
     */
    private List<TaskConstraints.ContextType> contextTypes;

    /**
     * 检索计划
     */
    private RetrievalPlan retrievalPlan;

    /**
     * 检索到的Chunk列表
     */
    @Builder.Default
    private List<RetrievedChunk> retrievedChunks = new ArrayList<>();

    /**
     * 当前草稿内容
     */
    private String currentDraft;

    /**
     * 历史草稿列表（用于版本追踪）
     */
    @Builder.Default
    private List<DraftVersion> draftHistory = new ArrayList<>();

    /**
     * 评估结果
     */
    private EvaluationResult evaluationResult;

    /**
     * 工具调用记录
     */
    @Builder.Default
    private List<ToolCallRecord> toolCallRecords = new ArrayList<>();

    /**
     * 修订轮次计数
     */
    @Builder.Default
    private int revisionCount = 0;

    /**
     * 检索重试次数
     */
    @Builder.Default
    private int retrievalRetryCount = 0;

    /**
     * 下一步行动建议
     */
    private WorkflowStage nextAction;

    /**
     * 最终交付物
     */
    private List<Artifact> finalArtifacts;

    /**
     * 错误信息
     */
    private ErrorInfo errorInfo;

    /**
     * 执行日志
     */
    @Builder.Default
    private List<StageExecutionLog> executionLogs = new ArrayList<>();

    /**
     * 扩展数据
     */
    @Builder.Default
    private Map<String, Object> extraData = new ConcurrentHashMap<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        CREATED("已创建"),
        RUNNING("运行中"),
        WAITING_FOR_INPUT("等待输入"),
        COMPLETED("已完成"),
        PARTIAL_COMPLETED("部分完成"),
        FAILED("已失败"),
        CANCELLED("已取消"),
        PAUSED("已暂停");

        private final String description;

        TaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == PARTIAL_COMPLETED || this == FAILED || this == CANCELLED;
        }
    }

    /**
     * 检索到的Chunk
     */
    @Data
    @Builder
    public static class RetrievedChunk implements Serializable {
        private static final long serialVersionUID = 1L;
        private String chunkId;
        private String content;
        private double similarityScore;
        private TaskConstraints.ContextType contextType;
        private Map<String, Object> metadata;
        private String sourceDocument;
        private int chapterNumber;
        private boolean isUsed;
    }

    /**
     * 草稿版本
     */
    @Data
    @Builder
    public static class DraftVersion implements Serializable {
        private static final long serialVersionUID = 1L;
        private int versionNumber;
        private String content;
        private WorkflowStage generatedAtStage;
        private LocalDateTime createdAt;
        private String changeDescription;
    }

    /**
     * 工具调用记录
     */
    @Data
    @Builder
    public static class ToolCallRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private String toolName;
        private String arguments;
        private String result;
        private boolean success;
        private LocalDateTime calledAt;
        private long executionTimeMs;
    }

    /**
     * 交付物/产物
     */
    @Data
    @Builder
    public static class Artifact implements Serializable {
        private static final long serialVersionUID = 1L;
        private String artifactId;
        private ArtifactType type;
        private String name;
        private String filePath;
        private long fileSize;
        private boolean available;

        public enum ArtifactType {
            PDF("PDF文档"),
            MARKDOWN("Markdown文档"),
            TEXT("文本文件"),
            JSON("JSON数据"),
            HTML("HTML文档");

            private final String description;

            ArtifactType(String description) {
                this.description = description;
            }

            public String getDescription() {
                return description;
            }
        }
    }

    /**
     * 错误信息
     */
    @Data
    @Builder
    public static class ErrorInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private ErrorType type;
        private String message;
        private String stackTrace;
        private WorkflowStage stageWhenError;
        private LocalDateTime occurredAt;

        public enum ErrorType {
            RETRIEVAL_ERROR("检索错误"),
            GENERATION_ERROR("生成错误"),
            EVALUATION_ERROR("评估错误"),
            DELIVERY_ERROR("交付错误"),
            VALIDATION_ERROR("验证错误"),
            TOOL_EXECUTION_ERROR("工具执行错误"),
            UNKNOWN_ERROR("未知错误");

            private final String description;

            ErrorType(String description) {
                this.description = description;
            }
        }
    }

    /**
     * 阶段执行日志
     */
    @Data
    @Builder
    public static class StageExecutionLog implements Serializable {
        private static final long serialVersionUID = 1L;
        private int stepNumber;
        private WorkflowStage stage;
        private String action;
        private String result;
        private boolean success;
        private LocalDateTime timestamp;
    }

    /**
     * 创建新任务上下文
     */
    public static TaskContext create(String sessionId) {
        return TaskContext.builder()
                .taskId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .sessionId(sessionId)
                .currentStage(WorkflowStage.INTENT_PARSE)
                .status(TaskStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 创建新任务上下文（带初始消息）
     */
    public static TaskContext create(String sessionId, String initialMessage) {
        TaskContext context = create(sessionId);
        context.setExtraData(Map.of("initialMessage", initialMessage));
        return context;
    }

    /**
     * 进入下一阶段
     */
    public void moveToNextStage() {
        if (this.currentStage != null && this.currentStage.isExecutableStage()) {
            this.currentStage = this.currentStage.nextStage();
            this.updatedAt = LocalDateTime.now();
            log.info("Task {} moved to stage: {}", taskId, this.currentStage);
        }
    }

    /**
     * 回跳到指定阶段
     */
    public boolean fallbackTo(WorkflowStage targetStage) {
        if (this.currentStage == null || !this.currentStage.canFallbackTo(targetStage)) {
            log.warn("Cannot fallback from {} to {}", this.currentStage, targetStage);
            return false;
        }
        this.currentStage = targetStage;
        this.updatedAt = LocalDateTime.now();
        log.info("Task {} fell back to stage: {}", taskId, targetStage);
        return true;
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String toolName, String arguments, String result, boolean success, long executionTimeMs) {
        if (this.toolCallRecords == null) {
            this.toolCallRecords = new ArrayList<>();
        }
        this.toolCallRecords.add(ToolCallRecord.builder()
                .toolName(toolName)
                .arguments(arguments)
                .result(result)
                .success(success)
                .calledAt(LocalDateTime.now())
                .executionTimeMs(executionTimeMs)
                .build());
    }

    /**
     * 添加执行日志
     */
    public void addExecutionLog(WorkflowStage stage, String action, String result, boolean success) {
        if (this.executionLogs == null) {
            this.executionLogs = new ArrayList<>();
        }
        this.executionLogs.add(StageExecutionLog.builder()
                .stepNumber(this.executionLogs.size() + 1)
                .stage(stage)
                .action(action)
                .result(result)
                .success(success)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * 保存草稿版本
     */
    public void saveDraft(String content, String changeDescription) {
        this.currentDraft = content;
        if (this.draftHistory == null) {
            this.draftHistory = new ArrayList<>();
        }
        this.draftHistory.add(DraftVersion.builder()
                .versionNumber(this.draftHistory.size() + 1)
                .content(content)
                .generatedAtStage(this.currentStage)
                .createdAt(LocalDateTime.now())
                .changeDescription(changeDescription)
                .build());
    }

    /**
     * 增加修订计数
     */
    public void incrementRevision() {
        this.revisionCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 增加检索重试计数
     */
    public void incrementRetrievalRetry() {
        this.retrievalRetryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置错误
     */
    public void setError(ErrorInfo.ErrorType type, String message, Exception e) {
        this.errorInfo = ErrorInfo.builder()
                .type(type)
                .message(message)
                .stackTrace(e != null ? e.toString() : null)
                .stageWhenError(this.currentStage)
                .occurredAt(LocalDateTime.now())
                .build();
        this.status = TaskStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 获取执行摘要
     */
    public String getExecutionSummary() {
        return String.format(
                "Task[id=%s, stage=%s, status=%s, revision=%d, retrievalRetries=%d, chunks=%d, tools=%d]",
                taskId, currentStage, status, revisionCount, retrievalRetryCount,
                retrievedChunks != null ? retrievedChunks.size() : 0,
                toolCallRecords != null ? toolCallRecords.size() : 0
        );
    }

    /**
     * 检查是否是死循环保护触发
     */
    public boolean isMaxRetriesReached(int maxRevisionRounds, int maxRetrievalRetries) {
        return this.revisionCount >= maxRevisionRounds || this.retrievalRetryCount >= maxRetrievalRetries;
    }
}