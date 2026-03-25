package com.qingluo.writeaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.qingluo.writeaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base agent abstraction used to manage state and the step-by-step execution loop.
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;

    private String systemPrompt;

    private String nextStepPrompt;

    private AgentState state = AgentState.IDLE;

    private int currentStep = 0;

    private int maxSteps = 10;

    private ChatClient chatClient;

    private List<Message> messageList = new ArrayList<>();

    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误：" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sendSafely(sseEmitter, "错误：无法从状态运行代理：" + this.state);
                    sendDoneSafely(sseEmitter);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sendSafely(sseEmitter, "错误：不能使用空提示词运行代理");
                    sendDoneSafely(sseEmitter);
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }

            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));
            List<String> results = new ArrayList<>();
            try {
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    sendSafely(sseEmitter, result);
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sendSafely(sseEmitter, "执行结束：达到最大步骤（" + maxSteps + "）");
                }
                sendDoneSafely(sseEmitter);
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sendSafely(sseEmitter, "执行错误：" + e.getMessage());
                    sendDoneSafely(sseEmitter);
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    private void sendSafely(SseEmitter sseEmitter, String message) throws IOException {
        sseEmitter.send(message);
    }

    private void sendDoneSafely(SseEmitter sseEmitter) throws IOException {
        sseEmitter.send("[DONE]");
    }

    public abstract String step();

    protected void cleanup() {
    }
}
