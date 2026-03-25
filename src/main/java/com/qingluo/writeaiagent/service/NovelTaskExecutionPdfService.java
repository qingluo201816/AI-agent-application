package com.qingluo.writeaiagent.service;

import com.qingluo.writeaiagent.agent.WriteManus;
import com.qingluo.writeaiagent.app.WriteApp;
import com.qingluo.writeaiagent.model.request.NovelChatRequest;
import com.qingluo.writeaiagent.service.support.AbstractNovelSseService;
import com.qingluo.writeaiagent.service.support.NovelPromptLoader;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能任务执行与 PDF 生成服务。
 */
@Service
public class NovelTaskExecutionPdfService extends AbstractNovelSseService {

    private static final String PROMPT_LOCATION = "classpath:prompts/novel/task-execution-pdf-system-prompt.txt";

    private static final String RAG_ENHANCEMENT_PROMPT = """
            你是小说任务上下文整理助手。
            请结合检索到的知识库信息和历史对话，只提取与当前任务直接相关的可靠上下文。

            输出结构：
            【任务理解】
            【可直接使用的上下文】
            【可能冲突或待确认】
            【缺失信息】

            要求：
            - 只保留与当前任务执行强相关的内容
            - 没有依据就明确写“未找到依据”
            - 不要代替后续工具执行任务
            - 不要虚构人物设定、剧情或世界观信息
            """;

    private static final String TASK_EXECUTION_NEXT_STEP_PROMPT = """
            请围绕“小说创作管理与归档”执行本轮任务：
            - 先明确任务目标、交付物和输出结构
            - 优先利用用户消息中提供的 RAG 增强上下文
            - 需要生成 PDF 时，先整理结构化内容，再调用 PDF 生成工具
            - 若需写入文件，文件名使用清晰的中文命名
            - 不要偏离到通用闲聊或无关分析
            - 若信息不足，只追问执行当前任务必需的最少问题
            """;

    private final ToolCallback[] allTools;

    private final ChatModel dashscopeChatModel;

    public NovelTaskExecutionPdfService(
            WriteApp writeApp,
            ChatSessionService chatSessionService,
            NovelPromptLoader promptLoader,
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel
    ) {
        super(writeApp, chatSessionService, promptLoader);
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
    }

    public SseEmitter chat(NovelChatRequest request) {
        String taskChatId = resolveChatId(request.chatId(), "novel-task-execution");
        String ragContext = writeApp.doChatWithRag(request.message(), taskChatId, RAG_ENHANCEMENT_PROMPT);
        String enhancedMessage = buildEnhancedMessage(request.message(), ragContext);

        WriteManus writeManus = new WriteManus(allTools, dashscopeChatModel);
        writeManus.setSystemPrompt(loadPrompt(PROMPT_LOCATION));
        writeManus.setNextStepPrompt(TASK_EXECUTION_NEXT_STEP_PROMPT);
        return writeManus.runStream(enhancedMessage);
    }

    private String buildEnhancedMessage(String message, String ragContext) {
        String resolvedContext = (ragContext == null || ragContext.isBlank()) ? "未检索到可用增强上下文。" : ragContext;
        return """
                当前小说任务请求：
                %s

                下面是结合小说知识库与历史上下文整理出的参考材料，请优先利用并按需核验：
                %s

                请开始执行任务。若需要生成 PDF，请先整理出结构化内容，再调用工具完成导出。
                """.formatted(message, resolvedContext);
    }
}
