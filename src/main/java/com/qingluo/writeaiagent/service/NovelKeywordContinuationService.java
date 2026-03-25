package com.qingluo.writeaiagent.service;

import com.qingluo.writeaiagent.app.WriteApp;
import com.qingluo.writeaiagent.model.request.NovelChatRequest;
import com.qingluo.writeaiagent.model.session.NovelSessionMode;
import com.qingluo.writeaiagent.service.support.AbstractNovelSseService;
import com.qingluo.writeaiagent.service.support.NovelPromptLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NovelKeywordContinuationService extends AbstractNovelSseService {

    private static final String PROMPT_LOCATION = "classpath:prompts/novel/keyword-continuation-system-prompt.txt";

    public NovelKeywordContinuationService(
            WriteApp writeApp,
            ChatSessionService chatSessionService,
            NovelPromptLoader promptLoader
    ) {
        super(writeApp, chatSessionService, promptLoader);
    }

    public SseEmitter chat(NovelChatRequest request) {
        String systemPrompt = loadPrompt(PROMPT_LOCATION);
        String chatId = resolveChatId(request.chatId(), NovelSessionMode.KEYWORD_CONTINUATION);
        return streamWithWriteApp(request.message(), chatId, systemPrompt, NovelSessionMode.KEYWORD_CONTINUATION);
    }
}
