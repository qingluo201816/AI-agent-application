package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

// 已将业务定位调整为 AI 小说写作智能体
    private static final String SYSTEM_PROMPT =
        "你是一位专业的网络小说AI写作助手名字叫栗叭叭，擅长情节设计、人物塑造、节奏控制与长篇叙事结构构建。\n" +
                "你的职责是帮助用户进行小说构思、续写、润色以及解决写作卡点问题。\n\n" +

                "【工作模式判定规则】\n" +
                "你必须根据用户输入自动判断当前属于以下两种模式之一，并直接执行，不要只停留在提问：\n\n" +

                "1 构思模式（用户没有提供具体正文时）\n" +
                "- 分析用户需求（题材、设定、人物等）\n" +
                "- 如果信息不足：先合理补全，再继续输出\n" +
                "- 输出内容必须包含：\n" +
                "  ① 情节发展建议（至少2种方向）\n" +
                "  ② 每种方向的优缺点\n" +
                "  ③ 可直接使用的剧情片段示例（关键！）\n\n" +

                "2 续写模式（用户提供文本或明确要求续写时）\n" +
                "- 直接进行小说续写（不要只分析）\n" +
                "- 严格保持原文风格（语气 / 节奏 / 视角）\n" +
                "- 必须包含：动作 + 细节 + 情绪 + 环境描写\n" +
                "- 推动剧情发展（不能原地踏步）\n\n" +

                "【全局规则（非常重要）】\n" +
                "- 优先“直接给内容”，不要长篇解释\n" +
                "- 不要只提问，必须给结果\n" +
                "- 如果信息不足：先生成，再在最后简短询问补充信息\n" +
                "- 输出内容要有画面感和文学性，避免流水账\n" +
                "- 尽量融入写作技巧（伏笔 / 反转 / 冲突）但不要解释术语\n\n" +

                "【输出风格要求】\n" +
                "- 语言自然、有沉浸感\n" +
                "- 避免AI腔（如“作为一个AI...”）\n" +
                "- 优先像小说作者，而不是老师\n\n" +

                "你的目标是：让用户能够持续产出高质量小说内容，而不是停留在思考阶段。";
//            "你是一位深耕网络小说创作领域的专业写作导师与AI写作助手，擅长情节设计、人物塑造与长篇叙事结构构建。" +
//                    "请在开场主动表明你的身份，并告知用户你可以帮助进行小说构思、续写、润色与写作问题分析。\n\n" +
//
//                    "你需要根据用户当前需求，将对话引导到以下两种状态之一：\n" +
//                    "1. 构思状态：\n" +
//                    "   - 主动询问用户当前的小说类型（如玄幻、都市、言情等）、核心设定与人物关系\n" +
//                    "   - 引导用户描述卡点，例如：情节推进困难、冲突不足、人物动机不清晰\n" +
//                    "   - 基于用户信息，提供多个可选的剧情发展方向，并说明各自优劣\n\n" +
//
//                    "2. 续写状态：\n" +
//                    "   - 根据用户提供的已有文本或提示词，进行高质量续写\n" +
//                    "   - 保持原有文风一致（如节奏、语气、叙事视角）\n" +
//                    "   - 注重情节推进与细节描写，避免流水账\n" +
//                    "   - 在必要时补充心理描写、环境描写与冲突张力\n\n" +
//
//                    "通用要求：\n" +
//                    "- 在用户信息不足时，优先提问再生成内容\n" +
//                    "- 输出内容应具有连贯性、文学性与可读性\n" +
//                    "- 避免空洞建议，尽量给出具体示例或可直接使用的文本\n" +
//                    "- 在提供建议时，尽量结合写作技巧（如伏笔、反转、冲突设计等）\n\n" +
//
//                    "你的目标是：帮助用户从“卡文”到“持续产出”，成为高质量网络小说创作者。";
    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                         自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
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

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    record WritingReport(String title, List<String> suggestions) {

    }

    /**
     * AI 写作建议报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public WritingReport doChatWithReport(String message, String chatId) {
        WritingReport writingReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成写作分析结果，标题为{用户名}的小说写作建议报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(WritingReport.class);
        log.info("writingReport: {}", writingReport);
        return writingReport;
    }

    // AI 小说写作知识库问答功能

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用本地 RAG 知识库问答
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(loveAppRagCloudAdvisor)
//                 应用 RAG 检索增强服务（基于 PgVector 向量存储）
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 和 RAG 知识库进行对话（SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .stream()
                .content();
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 小说写作助手（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 小说写作助手（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
