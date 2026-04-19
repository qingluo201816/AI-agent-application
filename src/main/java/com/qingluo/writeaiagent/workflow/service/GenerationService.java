package com.qingluo.writeaiagent.workflow.service;

import com.qingluo.writeaiagent.workflow.EvaluationResult;
import com.qingluo.writeaiagent.workflow.TaskConstraints;
import com.qingluo.writeaiagent.workflow.TaskContext;
import com.qingluo.writeaiagent.workflow.WorkflowStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生成服务
 * 负责基于检索结果生成符合要求的内容
 */
@Slf4j
@Service
public class GenerationService {

    private static final String GENERATION_SYSTEM_PROMPT = """
            你是小说创作专家，负责根据给定的上下文和约束条件生成高质量的小说内容。

            任务要求：
            1. 严格遵循用户指定的约束条件（字数、视角、风格、氛围等）
            2. 充分利用提供的上下文信息
            3. 确保生成内容的连贯性和一致性
            4. 包含必要的情节推进和细节描写
            5. 避免生成用户要求避免的内容

            输出格式：
            - 直接输出生成的内容，不要有前缀说明
            - 内容应该是完整的、可直接使用的
            """;

    private static final String GENERATION_USER_PROMPT_TEMPLATE = """
            【任务类型】
            %s

            【任务目标】
            %s

            【字数要求】
            最低%d字，目标%d字，最高%d字

            【视角要求】
            %s

            【风格要求】
            %s

            【氛围/基调】
            %s

            【必须包含的内容】
            %s

            【必须避免的内容】
            %s

            【参考上下文】
            %s

            【之前草稿（如果有修订）】
            %s

            【评估反馈（如果有）】
            %s

            请根据以上信息，生成符合要求的小说内容。
            """;

    private static final String REVISION_PROMPT = """
            【任务目标】
            %s

            【之前版本的问题】
            %s

            【字数要求】
            最低%d字，目标%d字，最高%d字

            【视角要求】
            %s

            【修复方向】
            %s

            【参考上下文】
            %s

            请在保留原版优点的基础上，针对问题进行修订，生成改进版本。
            """;

    private static final String LOCAL_REWRITE_PROMPT = """
            【任务目标】
            %s

            【之前版本的问题】
            %s

            【改写范围】
            %s

            【修复方向】
            %s

            【字数要求】
            最低%d字，目标%d字，最高%d字

            【视角要求】
            %s

            【参考上下文】
            %s

            请只对指定范围内的内容进行改写，保持其他部分不变，确保整体连贯性。
            """;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    public GenerationService(ChatModel dashscopeChatModel, RetrievalService retrievalService) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.retrievalService = retrievalService;
    }

    /**
     * 执行生成
     * @param context 任务上下文
     * @return 生成的草稿内容
     */
    public String generate(TaskContext context) {
        log.info("开始生成阶段: taskId={}, revisionCount={}", context.getTaskId(), context.getRevisionCount());

        String retrievalContext = retrievalService.buildRetrievalContext(context);
        String previousDraft = context.getCurrentDraft() != null ? context.getCurrentDraft() : "";
        
        // 构建包含评估反馈的提示
        String prompt = buildGenerationPrompt(context, retrievalContext, previousDraft);
        String generatedContent = callLlmForGeneration(prompt);

        // 记录草稿版本
        String draftDescription = context.getRevisionCount() > 0 ? "修订生成" : "初始生成";
        context.saveDraft(generatedContent, draftDescription);
        context.setCurrentDraft(generatedContent);
        context.setCurrentStage(WorkflowStage.GENERATE);
        context.addExecutionLog(WorkflowStage.GENERATE, "GENERATE",
                String.format("生成完成，字数%d字", generatedContent.length()), true);

        log.info("生成完成: taskId={}, length={}, draftHistory={}", 
                context.getTaskId(), generatedContent.length(), context.getDraftHistory().size());
        return generatedContent;
    }

    /**
     * 执行修订（基于评估反馈）
     * @param context 任务上下文
     * @param revisionFeedback 修订反馈
     * @return 修订后的草稿内容
     */
    public String revise(TaskContext context, String revisionFeedback) {
        log.info("开始修订阶段: taskId={}, revisionCount={}", context.getTaskId(), context.getRevisionCount());

        String retrievalContext = retrievalService.buildRetrievalContext(context);
        
        // 检查是否需要局部改写
        boolean needLocalRewrite = false;
        String rewriteScope = "全文";
        List<String> fixDirections = new ArrayList<>();
        
        // 从评估结果中获取修复建议
        EvaluationResult evaluationResult = context.getEvaluationResult();
        if (evaluationResult != null) {
            EvaluationResult.SuggestedGenerationFixes fixes = evaluationResult.getSuggestedGenerationFixes();
            if (fixes != null) {
                needLocalRewrite = fixes.isNeedLocalRewrite();
                rewriteScope = fixes.getRewriteScope() != null ? fixes.getRewriteScope() : "全文";
                if (fixes.getFixDirections() != null) {
                    fixDirections.addAll(fixes.getFixDirections());
                }
            }
        }

        String prompt;
        if (needLocalRewrite) {
            // 局部改写
            prompt = buildLocalRewritePrompt(context, revisionFeedback, retrievalContext, rewriteScope, fixDirections);
            log.info("执行局部改写，范围: {}", rewriteScope);
        } else {
            // 全文修订
            prompt = buildRevisionPrompt(context, revisionFeedback, retrievalContext, fixDirections);
            log.info("执行全文修订");
        }
        
        String revisedContent = callLlmForGeneration(prompt);

        // 记录草稿版本
        String draftDescription = "修订 v" + context.getRevisionCount() + " - " + (needLocalRewrite ? "局部改写" : "全文修订");
        context.saveDraft(revisedContent, draftDescription);
        context.setCurrentDraft(revisedContent);
        context.setCurrentStage(WorkflowStage.GENERATE);
        context.addExecutionLog(WorkflowStage.GENERATE, "REVISION",
                String.format("修订完成 v%d，字数%d字，类型: %s", 
                        context.getRevisionCount(), revisedContent.length(), 
                        needLocalRewrite ? "局部改写" : "全文修订"), true);

        log.info("修订完成: taskId={}, revision={}, length={}, type={}",
                context.getTaskId(), context.getRevisionCount(), revisedContent.length(),
                needLocalRewrite ? "局部改写" : "全文修订");
        return revisedContent;
    }

    private String buildGenerationPrompt(TaskContext context, String retrievalContext, String previousDraft) {
        TaskConstraints constraints = context.getConstraints();

        String taskTypeDesc = constraints.getTaskType() != null ?
                constraints.getTaskType().getDescription() : "自定义任务";

        String mustInclude = constraints.getMustInclude() != null && !constraints.getMustInclude().isEmpty() ?
                String.join("、", constraints.getMustInclude()) : "无";

        String mustAvoid = constraints.getMustAvoid() != null && !constraints.getMustAvoid().isEmpty() ?
                String.join("、", constraints.getMustAvoid()) : "无";

        String previousDraftSection = previousDraft.isEmpty() ? "无" : "【之前版本】\n" + previousDraft;

        // 添加评估反馈（如果有）
        String evaluationFeedback = "";
        EvaluationResult evaluationResult = context.getEvaluationResult();
        if (evaluationResult != null && !evaluationResult.isPassed()) {
            evaluationFeedback = "【评估反馈】\n";
            evaluationFeedback += "问题类型: " + evaluationResult.getIssueType().getDescription() + "\n";
            evaluationFeedback += "具体问题: " + evaluationResult.getProblemSummary() + "\n";
            
            // 添加详细问题
            if (!evaluationResult.getProblems().isEmpty()) {
                evaluationFeedback += "详细问题: " + evaluationResult.getProblems().stream()
                        .map(p -> p.getCategory().name() + " - " + p.getDescription())
                        .collect(Collectors.joining("；")) + "\n";
            }
            
            // 添加建议的生成修复
            if (evaluationResult.getSuggestedGenerationFixes() != null) {
                EvaluationResult.SuggestedGenerationFixes fixes = evaluationResult.getSuggestedGenerationFixes();
                if (!fixes.getFixDirections().isEmpty()) {
                    evaluationFeedback += "修复建议: " + String.join("；", fixes.getFixDirections()) + "\n";
                }
            }
        }

        return String.format(GENERATION_USER_PROMPT_TEMPLATE,
                taskTypeDesc,
                constraints.getGoalDescription(),
                constraints.getWordCount().getMin(),
                constraints.getWordCount().getTarget(),
                constraints.getWordCount().getMax(),
                constraints.getPov(),
                constraints.getStyle(),
                constraints.getTone(),
                mustInclude,
                mustAvoid,
                retrievalContext,
                previousDraftSection,
                evaluationFeedback
        );
    }

    private String buildRevisionPrompt(TaskContext context, String revisionFeedback, String retrievalContext, List<String> fixDirections) {
        TaskConstraints constraints = context.getConstraints();

        String fixDirectionsStr = fixDirections != null && !fixDirections.isEmpty() ?
                String.join("；", fixDirections) : "无";

        return String.format(REVISION_PROMPT,
                constraints.getGoalDescription(),
                revisionFeedback,
                constraints.getWordCount().getMin(),
                constraints.getWordCount().getTarget(),
                constraints.getWordCount().getMax(),
                constraints.getPov(),
                fixDirectionsStr,
                retrievalContext
        );
    }

    private String buildLocalRewritePrompt(TaskContext context, String revisionFeedback, String retrievalContext, 
                                         String rewriteScope, List<String> fixDirections) {
        TaskConstraints constraints = context.getConstraints();

        String fixDirectionsStr = fixDirections != null && !fixDirections.isEmpty() ?
                String.join("；", fixDirections) : "无";

        return String.format(LOCAL_REWRITE_PROMPT,
                constraints.getGoalDescription(),
                revisionFeedback,
                rewriteScope,
                fixDirectionsStr,
                constraints.getWordCount().getMin(),
                constraints.getWordCount().getTarget(),
                constraints.getWordCount().getMax(),
                constraints.getPov(),
                retrievalContext
        );
    }

    private String callLlmForGeneration(String prompt) {
        try {
            AssistantMessage response = chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage(GENERATION_SYSTEM_PROMPT),
                            new UserMessage(prompt)
                    ))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput();
            return response.getText();
        } catch (Exception e) {
            log.error("LLM生成调用失败: {}", e.getMessage());
            throw new RuntimeException("内容生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式生成（用于SSE输出）
     */
    public org.reactivestreams.Publisher<String> generateStream(TaskContext context) {
        log.info("开始流式生成: taskId={}", context.getTaskId());

        String retrievalContext = retrievalService.buildRetrievalContext(context);
        String prompt = buildGenerationPrompt(context, retrievalContext, "");

        context.setCurrentStage(WorkflowStage.GENERATE);

        return chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(GENERATION_SYSTEM_PROMPT),
                        new UserMessage(prompt)
                ))
                .stream()
                .content();
    }

    /**
     * 构建生成结果摘要（用于评估）
     */
    public String buildGenerationSummary(String content, TaskContext context) {
        StringBuilder summary = new StringBuilder();
        summary.append("【生成内容摘要】\n");
        summary.append(String.format("字数: %d字\n", content.length()));
        summary.append(String.format("任务类型: %s\n",
                context.getConstraints().getTaskType() != null ?
                        context.getConstraints().getTaskType().getDescription() : "未知"));
        summary.append(String.format("视角: %s\n", context.getConstraints().getPov()));
        summary.append(String.format("风格: %s\n", context.getConstraints().getStyle()));

        if (content.length() < 100) {
            summary.append(String.format("内容预览: %s", content));
        } else {
            summary.append(String.format("内容预览: %s...", content.substring(0, 100)));
        }

        return summary.toString();
    }
}