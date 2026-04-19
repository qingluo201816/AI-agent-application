package com.qingluo.writeaiagent.controller;

import com.qingluo.writeaiagent.model.request.NovelChatRequest;
import com.qingluo.writeaiagent.service.SuperAgentWorkflowService;
import com.qingluo.writeaiagent.workflow.TaskContext;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 右侧「AI 超级写作智能体」Controller
 * 仅服务于右侧超级智能体入口，使用新的状态驱动工作流
 */
@RestController
@RequestMapping("/ai/super-agent")
public class SuperAgentController {

    @Resource
    private SuperAgentWorkflowService superAgentWorkflowService;

    /**
     * 执行超级智能体工作流（SSE流式输出）
     */
    @PostMapping(
            value = "/chat/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter doChat(@Valid @RequestBody NovelChatRequest request) {
        return superAgentWorkflowService.execute(request);
    }

    /**
     * 获取任务状态
     */
    @GetMapping("/task/{taskId}/status")
    public Map<String, Object> getTaskStatus(@PathVariable String taskId) {
        TaskContext context = superAgentWorkflowService.getTaskContext(taskId);
        if (context == null) {
            return Map.of(
                    "exists", false,
                    "message", "任务不存在或已结束"
            );
        }
        return Map.of(
                "exists", true,
                "taskId", context.getTaskId(),
                "stage", context.getCurrentStage().name(),
                "status", context.getStatus().name(),
                "revisionCount", context.getRevisionCount(),
                "retrievalRetryCount", context.getRetrievalRetryCount(),
                "executionSummary", context.getExecutionSummary()
        );
    }

    /**
     * 取消任务
     */
    @PostMapping("/task/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        superAgentWorkflowService.cancelTask(taskId);
        return Map.of(
                "success", true,
                "message", "任务已取消"
        );
    }
}