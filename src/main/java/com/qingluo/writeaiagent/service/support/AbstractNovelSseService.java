package com.qingluo.writeaiagent.service.support;

import com.qingluo.writeaiagent.app.WriteApp;
import com.qingluo.writeaiagent.model.session.NovelSessionMode;
import com.qingluo.writeaiagent.service.ChatSessionService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.UUID;

public abstract class AbstractNovelSseService {

    private static final long SSE_TIMEOUT_MILLIS = 180000L;

    protected final WriteApp writeApp;

    protected final ChatSessionService chatSessionService;

    private final NovelPromptLoader promptLoader;

    protected AbstractNovelSseService(
            WriteApp writeApp,
            ChatSessionService chatSessionService,
            NovelPromptLoader promptLoader
    ) {
        this.writeApp = writeApp;
        this.chatSessionService = chatSessionService;
        this.promptLoader = promptLoader;
    }

    protected String loadPrompt(String location) {
        return promptLoader.load(location);
    }

    protected String resolveChatId(String chatId, NovelSessionMode mode) {
        return chatSessionService.ensureSession(mode, chatId);
    }

    protected String resolveChatId(String chatId, String prefix) {
        if (chatId == null || chatId.isBlank()) {
            return prefix + "-" + UUID.randomUUID();
        }
        return chatId;
    }

    protected SseEmitter streamWithWriteApp(
            String message,
            String chatId,
            String systemPrompt,
            NovelSessionMode mode
    ) {
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        chatSessionService.recordUserMessage(chatId, mode, message);
        StringBuilder assistantReply = new StringBuilder();
        Disposable disposable = writeApp.doChatWithRagByStream(message, chatId, systemPrompt)
                .concatWith(Mono.just("[DONE]"))
                .subscribe(
                        chunk -> {
                            if ("[DONE]".equals(chunk)) {
                                chatSessionService.recordAssistantReply(chatId, mode, message, assistantReply.toString());
                            } else {
                                assistantReply.append(chunk);
                            }
                            sendChunk(sseEmitter, chunk);
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete
                );
        sseEmitter.onCompletion(disposable::dispose);
        sseEmitter.onTimeout(() -> {
            disposable.dispose();
            sseEmitter.complete();
        });
        return sseEmitter;
    }

    protected void sendChunk(SseEmitter sseEmitter, String chunk) {
        try {
            sseEmitter.send(chunk);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to push stream chunk", e);
        }
    }
}
