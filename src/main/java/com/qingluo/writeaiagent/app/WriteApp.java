package com.qingluo.writeaiagent.app;

import com.qingluo.writeaiagent.advisor.MyLoggerAdvisor;
import com.qingluo.writeaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class WriteApp {

    private static final String WRITING_REPORT_PROMPT_SUFFIX =
            "每次对话后都要生成写作分析结果，标题为{用户名}的小说写作建议报告，内容为建议列表";

    private final ChatClient chatClient;

    private final String systemPrompt;

    public WriteApp(
            ChatModel dashscopeChatModel,
            ChatMemory chatMemory,
            @Value("classpath:prompts/write-app-system-prompt.txt") org.springframework.core.io.Resource systemPromptResource
    ) {
        this.systemPrompt = loadPrompt(systemPromptResource);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    private String loadPrompt(org.springframework.core.io.Resource promptResource) {
        try {
            return StreamUtils.copyToString(promptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载系统提示词文件失败: " + promptResource.getDescription(), e);
        }
    }

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    public record WritingReport(String title, List<String> suggestions) {
    }

    public WritingReport doChatWithReport(String message, String chatId) {
        WritingReport writingReport = chatClient
                .prompt()
                .system(systemPrompt + System.lineSeparator() + WRITING_REPORT_PROMPT_SUFFIX)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(WritingReport.class);
        log.info("writingReport: {}", writingReport);
        return writingReport;
    }

    @Resource
    private VectorStore writeAppVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    public String doChatWithRag(String message, String chatId) {
        return doChatWithRag(message, chatId, systemPrompt);
    }

    public String doChatWithRag(String message, String chatId, String systemPromptOverride) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(resolveSystemPrompt(systemPromptOverride))
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(writeAppVectorStore))
//                .advisors(
//                        WriteAppRagCustomAdvisorFactory.createWriteAppRagCustomAdvisor(
//                                writeAppVectorStore, "主角"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        return doChatWithRagByStream(message, chatId, systemPrompt);
    }

    public Flux<String> doChatWithRagByStream(String message, String chatId, String systemPromptOverride) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                .system(resolveSystemPrompt(systemPromptOverride))
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(writeAppVectorStore))
                .stream()
                .content();
    }

    private String resolveSystemPrompt(String systemPromptOverride) {
        if (systemPromptOverride == null || systemPromptOverride.isBlank()) {
            return systemPrompt;
        }
        return systemPromptOverride;
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
