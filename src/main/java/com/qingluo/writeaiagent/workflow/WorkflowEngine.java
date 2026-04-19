package com.qingluo.writeaiagent.workflow;

import com.qingluo.writeaiagent.workflow.service.DeliveryService;
import com.qingluo.writeaiagent.workflow.service.EvaluationService;
import com.qingluo.writeaiagent.workflow.service.GenerationService;
import com.qingluo.writeaiagent.workflow.service.IntentParseService;
import com.qingluo.writeaiagent.workflow.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import com.qingluo.writeaiagent.workflow.TaskContext.Artifact;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 工作流引擎 / 任务编排器
 * 负责调度各个阶段、控制状态流转、防止死循环、汇总执行日志
 */
@Slf4j
@Component
public class WorkflowEngine {

    private static final int DEFAULT_MAX_REVISION_ROUNDS = 3;
    private static final int DEFAULT_MAX_RETRIEVAL_RETRIES = 2;
    private static final int DEFAULT_MAX_TOTAL_STEPS = 20;

    private final IntentParseService intentParseService;
    private final RetrievalService retrievalService;
    private final GenerationService generationService;
    private final EvaluationService evaluationService;
    private final DeliveryService deliveryService;

    public WorkflowEngine(
            IntentParseService intentParseService,
            RetrievalService retrievalService,
            GenerationService generationService,
            EvaluationService evaluationService,
            DeliveryService deliveryService
    ) {
        this.intentParseService = intentParseService;
        this.retrievalService = retrievalService;
        this.generationService = generationService;
        this.evaluationService = evaluationService;
        this.deliveryService = deliveryService;
    }

    /**
     * 执行完整工作流（流式版本，用于SSE）
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param sessionHistory 会话历史
     * @param eventConsumer 事件消费者，用于处理各个阶段的输出
     */
    public void executeStream(
            String userInput,
            String sessionId,
            List<Message> sessionHistory,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        log.info("流式工作流开始: sessionId={}", sessionId);

        TaskContext context = TaskContext.create(sessionId, userInput);
        context.setStatus(TaskContext.TaskStatus.RUNNING);

        try {
            emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_START, context,
                    "意图识别阶段开始");

            context = executeIntentParseWithEvents(context, sessionHistory, eventConsumer);

            emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_START, context,
                    "检索阶段开始");
            context = executeRetrievalWithEvents(context, eventConsumer);

            emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_START, context,
                    "生成阶段开始");
            context = executeGenerationWithEvents(context, eventConsumer);

            emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_START, context,
                    "评估阶段开始");
            context = executeEvaluationWithEvents(context, eventConsumer);

            if (context.getStatus() == TaskContext.TaskStatus.RUNNING) {
                emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_START, context,
                        "交付阶段开始");
                context = executeDeliveryWithEvents(context, eventConsumer);
            }

            if (context.getStatus() == TaskContext.TaskStatus.RUNNING) {
                context.setStatus(TaskContext.TaskStatus.COMPLETED);
                context.setCurrentStage(WorkflowStage.COMPLETED);
                emitEvent(eventConsumer, WorkflowEvent.Type.COMPLETED, context,
                        "工作流完成");
            }

        } catch (Exception e) {
            log.error("流式工作流执行异常: {}", e.getMessage(), e);
            context.setError(TaskContext.ErrorInfo.ErrorType.UNKNOWN_ERROR, e.getMessage(), e);
            emitEvent(eventConsumer, WorkflowEvent.Type.ERROR, context,
                    "工作流异常: " + e.getMessage());
        }
    }

    private TaskContext executeIntentParse(TaskContext context, List<Message> sessionHistory) {
        String userInput = (String) context.getExtraData().get("initialMessage");
        context = intentParseService.parseIntent(
                userInput,
                sessionHistory
        );
        context.setCurrentStage(WorkflowStage.RETRIEVE);
        return context;
    }

    private TaskContext executeIntentParseWithEvents(
            TaskContext context,
            List<Message> sessionHistory,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                "正在分析任务意图...");
        context = executeIntentParse(context, sessionHistory);
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                "意图识别完成: " + context.getTaskType().getDescription());
        return context;
    }

    private TaskContext executeRetrieval(TaskContext context, int maxRetrievalRetries) {
        int retryCount = 0;
        while (true) {
            try {
                context = retrievalService.retrieve(context);
                break;
            } catch (Exception e) {
                if (retryCount < maxRetrievalRetries) {
                    log.warn("检索失败，重试 {}/{}: {}", retryCount + 1, maxRetrievalRetries, e.getMessage());
                    context = retrievalService.reRetrieve(context);
                    retryCount++;
                } else {
                    log.error("检索最终失败", e);
                    context.setError(TaskContext.ErrorInfo.ErrorType.RETRIEVAL_ERROR,
                            "检索失败: " + e.getMessage(), e);
                    break;
                }
            }
        }
        context.setCurrentStage(WorkflowStage.GENERATE);
        return context;
    }

    private TaskContext executeRetrievalWithEvents(
            TaskContext context,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                "正在检索相关上下文...");
        context = executeRetrieval(context, DEFAULT_MAX_RETRIEVAL_RETRIES);
        int chunkCount = context.getRetrievedChunks() != null ? context.getRetrievedChunks().size() : 0;
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                String.format("检索完成，召回%d个相关片段", chunkCount));
        return context;
    }

    private TaskContext executeGeneration(TaskContext context) {
        String content = generationService.generate(context);
        context.setCurrentStage(WorkflowStage.EVALUATE);
        return context;
    }

    private TaskContext executeGenerationWithEvents(
            TaskContext context,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                "正在生成内容...");
        context = executeGeneration(context);
        int wordCount = context.getCurrentDraft() != null ? context.getCurrentDraft().length() : 0;
        emitEvent(eventConsumer, WorkflowEvent.Type.DRAFT_GENERATED, context,
                String.format("内容生成完成，共%d字", wordCount));
        return context;
    }

    private TaskContext executeEvaluationAndDecide(
            TaskContext context,
            int maxRevisionRounds,
            int maxRetrievalRetries
    ) {
        while (true) {
            if (context.isMaxRetriesReached(maxRevisionRounds, maxRetrievalRetries)) {
                log.warn("达到最大重试次数限制: revision={}, retrieval={}",
                        context.getRevisionCount(), context.getRetrievalRetryCount());
                context.addExecutionLog(WorkflowStage.EVALUATE, "MAX_RETRIES_REACHED",
                        "达到最大重试次数，强制进入交付阶段", true);
                // 设置为部分完成状态，带有告警
                context.setStatus(TaskContext.TaskStatus.PARTIAL_COMPLETED);
                context.addExecutionLog(WorkflowStage.EVALUATE, "ALERT",
                        "由于达到最大重试次数，任务部分完成，可能存在质量问题", true);
                break;
            }

            EvaluationResult result = evaluationService.evaluate(context.getCurrentDraft(), context);
            context.setEvaluationResult(result);

            if (result.isPassed()) {
                log.info("评估通过: taskId={}", context.getTaskId());
                break;
            }

            WorkflowStage fallbackStage = result.getSuggestedNextStage();
            if (fallbackStage == null || !context.getCurrentStage().canFallbackTo(fallbackStage)) {
                log.warn("无法回跳到建议阶段 {}，当前阶段 {}，强制结束",
                        fallbackStage, context.getCurrentStage());
                break;
            }

            log.info("评估未通过，进入{}阶段重试", fallbackStage);

            switch (fallbackStage) {
                case RETRIEVE -> {
                    context.fallbackTo(WorkflowStage.RETRIEVE);
                    context.incrementRetrievalRetry();
                    log.info("回跳到检索阶段，当前检索重试次数: {}/{}", 
                            context.getRetrievalRetryCount(), maxRetrievalRetries);
                    context = executeRetrieval(context, maxRetrievalRetries);
                    context = executeGeneration(context);
                }
                case GENERATE -> {
                    context.fallbackTo(WorkflowStage.GENERATE);
                    context.incrementRevision();
                    log.info("回跳到生成阶段，当前修订次数: {}/{}", 
                            context.getRevisionCount(), maxRevisionRounds);
                    String feedback = evaluationService.buildRevisionFeedback(result);
                    generationService.revise(context, feedback);
                }
                case DELIVER -> {
                    log.info("进入交付阶段");
                    break;
                }
                default -> {
                    log.warn("未知的回跳目标: {}", fallbackStage);
                    break;
                }
            }
        }

        context.setCurrentStage(WorkflowStage.DELIVER);
        return context;
    }

    private TaskContext executeEvaluationWithEvents(
            TaskContext context,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        int iteration = 0;
        while (true) {
            iteration++;
            if (iteration > DEFAULT_MAX_REVISION_ROUNDS * 2 + 2) {
                emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                        "评估循环次数过多，强制结束");
                break;
            }

            if (context.isMaxRetriesReached(DEFAULT_MAX_REVISION_ROUNDS, DEFAULT_MAX_RETRIEVAL_RETRIES)) {
                emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                        "达到最大重试次数，结束评估");
                break;
            }

            emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                    "正在进行质量评估...");

            EvaluationResult result = evaluationService.evaluate(context.getCurrentDraft(), context);
            context.setEvaluationResult(result);

            if (result.isPassed()) {
                emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                        "✅ 内容通过评估");
                break;
            }

            String feedback = evaluationService.buildRevisionFeedback(result);
            emitEvent(eventConsumer, WorkflowEvent.Type.EVALUATION_FEEDBACK, context, feedback);

            WorkflowStage fallbackStage = result.getSuggestedNextStage();
            if (fallbackStage == null || !context.getCurrentStage().canFallbackTo(fallbackStage)) {
                emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                        "评估结束，进入交付阶段");
                break;
            }

            switch (fallbackStage) {
                case RETRIEVE -> {
                    emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                            "🔄 回跳到检索阶段补充信息...");
                    context.fallbackTo(WorkflowStage.RETRIEVE);
                    context.incrementRetrievalRetry();
                    emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                            String.format("检索重试次数: %d/%d",
                                    context.getRetrievalRetryCount(), DEFAULT_MAX_RETRIEVAL_RETRIES));
                    context = executeRetrievalWithEvents(context, eventConsumer);
                    context = executeGenerationWithEvents(context, eventConsumer);
                }
                case GENERATE -> {
                    emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                            "🔄 回跳到生成阶段进行修订...");
                    context.fallbackTo(WorkflowStage.GENERATE);
                    context.incrementRevision();
                    emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                            String.format("修订次数: %d/%d",
                                    context.getRevisionCount(), DEFAULT_MAX_REVISION_ROUNDS));
                    generationService.revise(context, feedback);
                    int wordCount = context.getCurrentDraft() != null ? context.getCurrentDraft().length() : 0;
                    emitEvent(eventConsumer, WorkflowEvent.Type.DRAFT_GENERATED, context,
                            String.format("修订版本生成完成，共%d字", wordCount));
                }
                default -> {
                    break;
                }
            }
        }

        context.setCurrentStage(WorkflowStage.DELIVER);
        return context;
    }

    private TaskContext executeDelivery(TaskContext context) {
        try {
            context = deliveryService.deliver(context);
            if (context.getFinalArtifacts() != null && !context.getFinalArtifacts().isEmpty()) {
                context.setStatus(TaskContext.TaskStatus.COMPLETED);
            }
        } catch (Exception e) {
            log.error("交付失败", e);
            context.setError(TaskContext.ErrorInfo.ErrorType.DELIVERY_ERROR, e.getMessage(), e);
        }
        context.setCurrentStage(WorkflowStage.COMPLETED);
        return context;
    }

    private TaskContext executeDeliveryWithEvents(
            TaskContext context,
            Consumer<WorkflowEvent> eventConsumer
    ) {
        emitEvent(eventConsumer, WorkflowEvent.Type.STAGE_PROGRESS, context,
                "正在生成最终交付物...");

        context = executeDelivery(context);

        if (context.getFinalArtifacts() != null && !context.getFinalArtifacts().isEmpty()) {
            String report = deliveryService.buildDeliveryReport(context);
            emitEvent(eventConsumer, WorkflowEvent.Type.DELIVERY_COMPLETE, context, report);
        } else if (context.getErrorInfo() != null) {
            emitEvent(eventConsumer, WorkflowEvent.Type.ERROR, context,
                    "❌ 交付失败: " + context.getErrorInfo().getMessage());
        }

        return context;
    }

    private void emitEvent(Consumer<WorkflowEvent> consumer, WorkflowEvent.Type type, TaskContext context, String message) {
        consumer.accept(WorkflowEvent.builder()
                .type(type)
                .taskId(context.getTaskId())
                .stage(context.getCurrentStage())
                .message(message)
                .timestamp(java.time.LocalDateTime.now())
                .draft(context.getCurrentDraft())
                .evaluationResult(context.getEvaluationResult())
                .artifacts(context.getFinalArtifacts())
                .build());
    }

    /**
     * 工作流事件
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class WorkflowEvent {
        public enum Type {
            STAGE_START,
            STAGE_PROGRESS,
            DRAFT_GENERATED,
            EVALUATION_FEEDBACK,
            DELIVERY_COMPLETE,
            COMPLETED,
            ERROR
        }

        private Type type;
        private String taskId;
        private WorkflowStage stage;
        private String message;
        private java.time.LocalDateTime timestamp;
        private String draft;
        private EvaluationResult evaluationResult;
        private List<Artifact> artifacts;
    }
}
