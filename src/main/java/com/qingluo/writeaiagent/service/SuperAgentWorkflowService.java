package com.qingluo.writeaiagent.service;

import com.qingluo.writeaiagent.chatmemory.FileBasedChatMemory;
import com.qingluo.writeaiagent.model.request.NovelChatRequest;
import com.qingluo.writeaiagent.workflow.EvaluationResult;
import com.qingluo.writeaiagent.workflow.TaskContext;
import com.qingluo.writeaiagent.workflow.WorkflowEngine;
import com.qingluo.writeaiagent.workflow.WorkflowEngine.WorkflowEvent;
import com.qingluo.writeaiagent.workflow.WorkflowStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 超级智能体工作流服务
 * 面向右侧「AI 超级写作智能体」入口
 * 使用状态驱动的 Workflow Engine 执行任务
 */
@Slf4j
@Service
public class SuperAgentWorkflowService {

    private static final long SSE_TIMEOUT_MILLIS = 300000L;

    private final WorkflowEngine workflowEngine;
    private final FileBasedChatMemory chatMemory;

    private final Map<String, TaskContext> activeContexts = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sessionDrafts = new ConcurrentHashMap<>();

    public SuperAgentWorkflowService(WorkflowEngine workflowEngine, FileBasedChatMemory chatMemory) {
        this.workflowEngine = workflowEngine;
        this.chatMemory = chatMemory;
    }

    /**
     * 执行超级智能体工作流（SSE流式输出）
     */
    public SseEmitter execute(NovelChatRequest request) {
        String chatId = resolveChatId(request.chatId());
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        try {
            chatMemory.add(chatId, List.of(
                    new org.springframework.ai.chat.messages.UserMessage(request.message())
            ));
        } catch (Exception e) {
            log.warn("记录用户消息失败: {}", e.getMessage());
        }

        String finalChatId = chatId;
        List<String> draftAccumulator = new CopyOnWriteArrayList<>();

        sseEmitter.onCompletion(() -> {
            log.info("SSE连接完成: chatId={}", finalChatId);
            if (!draftAccumulator.isEmpty()) {
                String fullDraft = String.join("", draftAccumulator);
                sessionDrafts.put(finalChatId, draftAccumulator);
                try {
                    chatMemory.add(finalChatId, List.of(
                            new org.springframework.ai.chat.messages.AssistantMessage(fullDraft)
                    ));
                } catch (Exception e) {
                    log.warn("记录助手回复失败: {}", e.getMessage());
                }
            }
        });

        sseEmitter.onTimeout(() -> {
            log.warn("SSE连接超时: chatId={}", finalChatId);
            sseEmitter.complete();
        });

        CompletableFuture.runAsync(() -> workflowEngine.executeStream(
                request.message(),
                chatId,
                chatMemory.get(chatId),
                event -> handleWorkflowEvent(event, sseEmitter, draftAccumulator)
        )).exceptionally(ex -> {
            log.error("异步执行工作流失败: chatId={}, error={}", finalChatId, ex.getMessage(), ex);
            try {
                send(sseEmitter, "");
                send(sseEmitter, "【错误】工作流执行失败: " + ex.getMessage());
                sseEmitter.complete();
            } catch (Exception ignored) {
                log.warn("异步失败后的SSE收尾发送失败: chatId={}", finalChatId);
            }
            return null;
        });

        return sseEmitter;
    }

    private void handleWorkflowEvent(
            WorkflowEvent event,
            SseEmitter sseEmitter,
            List<String> draftAccumulator
    ) {
        try {
            switch (event.getType()) {
                case STAGE_START -> {
                    send(sseEmitter, "【阶段】" + event.getStage().getDescription());
                }
                case STAGE_PROGRESS -> {
                    if (event.getMessage().contains("意图识别")
                            || event.getMessage().contains("检索")
                            || event.getMessage().contains("生成")
                            || event.getMessage().contains("评估")
                            || event.getMessage().contains("交付")) {
                        send(sseEmitter, event.getMessage());
                    } else {
                        send(sseEmitter, "  " + event.getMessage());
                    }
                }
                case DRAFT_GENERATED -> {
                    send(sseEmitter, "");
                    send(sseEmitter, "【生成内容】");
                    send(sseEmitter, event.getDraft());
                    draftAccumulator.add(event.getDraft() != null ? event.getDraft() : "");
                }
                case EVALUATION_FEEDBACK -> {
                    send(sseEmitter, "");
                    send(sseEmitter, "【评估反馈】");
                    send(sseEmitter, event.getMessage());
                }
                case DELIVERY_COMPLETE -> {
                    send(sseEmitter, "");
                    send(sseEmitter, event.getMessage());
                }
                case COMPLETED -> {
                    send(sseEmitter, "");
                    send(sseEmitter, "【任务完成】");
                    send(sseEmitter, "工作流执行完成，任务ID: " + event.getTaskId());
                    log.info("发送完成事件: taskId={}", event.getTaskId());
                    sseEmitter.complete();
                    log.info("调用 emitter.complete() 完成 SSE 连接: taskId={}", event.getTaskId());
                }
                case ERROR -> {
                    send(sseEmitter, "");
                    send(sseEmitter, "【错误】" + event.getMessage());
                    log.info("发送错误事件: {}", event.getMessage());
                    sseEmitter.complete();
                    log.info("调用 emitter.complete() 完成 SSE 连接");
                }
            }
        } catch (Exception e) {
            log.error("发送SSE事件失败: {}", e.getMessage());
        }
    }

    private void send(SseEmitter sseEmitter, String content) {
        try {
            sseEmitter.send(content);
        } catch (IOException e) {
            log.warn("SSE发送失败: {}", e.getMessage());
        }
    }

    private String resolveChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return "super_agent_" + System.currentTimeMillis();
        }
        return chatId;
    }

    /**
     * 获取当前任务状态
     */
    public TaskContext getTaskContext(String taskId) {
        return activeContexts.get(taskId);
    }

    /**
     * 取消任务
     */
    public void cancelTask(String taskId) {
        TaskContext context = activeContexts.get(taskId);
        if (context != null) {
            context.setStatus(TaskContext.TaskStatus.CANCELLED);
            activeContexts.remove(taskId);
        }
    }
}
