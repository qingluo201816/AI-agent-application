package com.qingluo.writeaiagent.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.writeaiagent.workflow.RetrievalPlan;
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
 * 意图识别 + 结构化约束服务
 * 负责分析用户输入，生成结构化任务信息
 */
@Slf4j
@Service
public class IntentParseService {

    private static final String INTENT_PARSE_SYSTEM_PROMPT = """
            你是小说写作任务的意图识别专家。
            你的职责是分析用户输入，生成结构化的任务约束信息。

            你必须输出一个JSON对象，包含以下字段：
            - taskType: 任务类型，取值范围：chapter_continue, plot_trace, inspiration, setting_check, character_archive, world_building, timeline_sort, export_pdf, custom_task
            - wordCount: 字数要求，格式为 {"min": 最小字数, "max": 最大字数, "target": 目标字数}
            - pov: 视角要求（如"主角视角"、"第三人称"等）
            - style: 风格要求（如"网络小说风"、"文学性"等）
            - tone: 氛围/基调要求（如"紧张"、"轻松"、"悬疑"等）
            - goalDescription: 任务目标的中文描述
            - mustInclude: 必须包含的内容列表
            - mustAvoid: 必须避免的内容列表
            - outputRequirements: 输出要求，格式为 {"requirePdf": true/false, "fileName": "文件名（不含扩展名）"}
            - contextTypes: 后续检索需要的上下文类型数组，取值范围：recent_chapters, character_profiles, active_plotline, timeline, mystery_clues, world_setting, relationship_map, previous_drafts, theme_analysis, writing_style

            约束补全规则：
            - 如果用户没有指定字数，默认1500-3000字
            - 如果用户没有指定视角，默认使用主角视角
            - contextTypes必须根据taskType自动推断，例如：
              * chapter_continue 需要: recent_chapters, writing_style
              * character_archive 需要: character_profiles, relationship_map
              * plot_trace 需要: recent_chapters, active_plotline, timeline
              * export_pdf 需要: recent_chapters, previous_drafts

            输出格式：
            只输出JSON，不要有其他解释性文字。
            """;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    public IntentParseService(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 执行意图识别
     */
    public TaskContext parseIntent(String userInput, List<Message> sessionHistory) {
        TaskContext context = TaskContext.create("temp-session", userInput);

        String structuredJson = callLlmForIntent(userInput, sessionHistory);
        TaskConstraints constraints = parseConstraintsFromJson(structuredJson, userInput);

        context.setConstraints(constraints);
        context.setTaskType(constraints.getTaskType());
        context.setContextTypes(constraints.getContextTypes());

        RetrievalPlan retrievalPlan = buildRetrievalPlan(constraints);
        context.setRetrievalPlan(retrievalPlan);

        context.setCurrentStage(WorkflowStage.INTENT_PARSE);
        context.addExecutionLog(WorkflowStage.INTENT_PARSE, "INTENT_PARSE",
                "意图识别完成，任务类型: " + constraints.getTaskType().getDescription(), true);

        log.info("意图识别完成: taskType={}, wordCount={}-{}",
                constraints.getTaskType(),
                constraints.getWordCount().getMin(),
                constraints.getWordCount().getMax());

        return context;
    }

    /**
     * 更新已有上下文的意图
     */
    public TaskContext reparseIntent(TaskContext existingContext, String additionalInput) {
        String combinedInput = existingContext.getConstraints().getOriginalInput() + "\n\n补充信息: " + additionalInput;
        existingContext.getConstraints().setOriginalInput(combinedInput);

        String structuredJson = callLlmForIntent(combinedInput, null);
        TaskConstraints newConstraints = parseConstraintsFromJson(structuredJson, combinedInput);

        existingContext.setConstraints(newConstraints);
        existingContext.setTaskType(newConstraints.getTaskType());
        existingContext.setContextTypes(newConstraints.getContextTypes());

        RetrievalPlan retrievalPlan = buildRetrievalPlan(newConstraints);
        existingContext.setRetrievalPlan(retrievalPlan);

        existingContext.addExecutionLog(WorkflowStage.INTENT_PARSE, "INTENT_REPARSE",
                "意图重新识别完成，任务类型: " + newConstraints.getTaskType().getDescription(), true);

        return existingContext;
    }

    private String callLlmForIntent(String userInput, List<Message> sessionHistory) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(INTENT_PARSE_SYSTEM_PROMPT));

        if (sessionHistory != null && !sessionHistory.isEmpty()) {
            String historySummary = sessionHistory.stream()
                    .map(m -> m.getMessageType() + ": " + m.getText())
                    .collect(Collectors.joining("\n"));
            messages.add(new UserMessage("【对话历史】\n" + historySummary));
        }

        messages.add(new UserMessage("【当前任务】\n" + userInput));

        try {
            AssistantMessage response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput();
            String text = response.getText();
            log.debug("LLM意图识别原始输出: {}", text);
            return text;
        } catch (Exception e) {
            log.error("意图识别LLM调用失败", e);
            return createDefaultConstraintsJson(userInput);
        }
    }

    private TaskConstraints parseConstraintsFromJson(String jsonStr, String originalInput) {
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            return buildConstraintsFromJson(root, originalInput);
        } catch (Exception e) {
            log.warn("JSON解析失败，使用默认约束: {}", e.getMessage());
            return createDefaultConstraints(originalInput);
        }
    }

    private TaskConstraints buildConstraintsFromJson(JsonNode root, String originalInput) throws Exception {
        TaskConstraints.TaskType taskType = parseTaskType(getTextOrDefault(root, "taskType", "custom_task"));
        List<TaskConstraints.ContextType> contextTypes = parseContextTypes(root.path("contextTypes"));

        JsonNode wordCountNode = root.path("wordCount");
        TaskConstraints.WordCountRequirement wordCount = TaskConstraints.WordCountRequirement.builder()
                .min(wordCountNode.path("min").asInt(1500))
                .max(wordCountNode.path("max").asInt(3000))
                .target(wordCountNode.path("target").asInt(2000))
                .unit("字")
                .build();

        List<String> mustInclude = new ArrayList<>();
        root.path("mustInclude").forEach(node -> mustInclude.add(node.asText()));

        List<String> mustAvoid = new ArrayList<>();
        root.path("mustAvoid").forEach(node -> mustAvoid.add(node.asText()));

        JsonNode outputReqNode = root.path("outputRequirements");
        TaskConstraints.OutputRequirements outputRequirements = TaskConstraints.OutputRequirements.builder()
                .requirePdf(outputReqNode.path("requirePdf").asBoolean(false))
                .fileName(getTextOrDefault(outputReqNode, "fileName", null))
                .format(getTextOrDefault(outputReqNode, "format", "markdown"))
                .includeMetadata(true)
                .outputDirectory("output")
                .build();

        return TaskConstraints.builder()
                .taskType(taskType)
                .originalInput(originalInput)
                .wordCount(wordCount)
                .pov(getTextOrDefault(root, "pov", "主角视角"))
                .style(getTextOrDefault(root, "style", "网络小说风"))
                .tone(getTextOrDefault(root, "tone", "中性"))
                .goalDescription(getTextOrDefault(root, "goalDescription", "完成小说创作任务"))
                .mustInclude(mustInclude)
                .mustAvoid(mustAvoid)
                .outputRequirements(outputRequirements)
                .contextTypes(contextTypes)
                .build();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() || fieldNode.isNull() ? defaultValue : fieldNode.asText(defaultValue);
    }

    private List<TaskConstraints.ContextType> parseContextTypes(JsonNode contextTypesNode) {
        List<TaskConstraints.ContextType> types = new ArrayList<>();
        if (contextTypesNode.isArray()) {
            for (JsonNode node : contextTypesNode) {
                try {
                    types.add(TaskConstraints.ContextType.valueOf(node.asText().toUpperCase().replace("-", "_")));
                } catch (IllegalArgumentException e) {
                    log.debug("未知的上下文类型: {}", node.asText());
                }
            }
        }
        if (types.isEmpty()) {
            types.add(TaskConstraints.ContextType.RECENT_CHAPTERS);
            types.add(TaskConstraints.ContextType.WRITING_STYLE);
        }
        return types;
    }

    private TaskConstraints.TaskType parseTaskType(String typeStr) {
        try {
            return TaskConstraints.TaskType.valueOf(typeStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return TaskConstraints.TaskType.CUSTOM_TASK;
        }
    }

    private String createDefaultConstraintsJson(String userInput) {
        String truncatedInput = userInput.length() > 100 ? userInput.substring(0, 100) : userInput;
        return String.format("""
                {
                    "taskType": "custom_task",
                    "wordCount": {"min": 1500, "max": 3000, "target": 2000},
                    "pov": "主角视角",
                    "style": "网络小说风",
                    "tone": "中性",
                    "goalDescription": "%s",
                    "mustInclude": [],
                    "mustAvoid": [],
                    "outputRequirements": {"requirePdf": false},
                    "contextTypes": ["recent_chapters", "writing_style"]
                }
                """, truncatedInput);
    }

    private TaskConstraints createDefaultConstraints(String userInput) {
        return TaskConstraints.builder()
                .taskType(TaskConstraints.TaskType.CUSTOM_TASK)
                .originalInput(userInput)
                .wordCount(TaskConstraints.WordCountRequirement.of(1500, 3000))
                .pov("主角视角")
                .style("网络小说风")
                .tone("中性")
                .goalDescription(userInput)
                .mustInclude(new ArrayList<>())
                .mustAvoid(new ArrayList<>())
                .outputRequirements(TaskConstraints.OutputRequirements.defaultRequirements())
                .contextTypes(List.of(TaskConstraints.ContextType.RECENT_CHAPTERS, TaskConstraints.ContextType.WRITING_STYLE))
                .build();
    }

    private RetrievalPlan buildRetrievalPlan(TaskConstraints constraints) {
        List<RetrievalPlan.RetrievalConfig> configs = constraints.getContextTypes().stream()
                .map(ct -> RetrievalPlan.RetrievalConfig.of(ct, constraints.getGoalDescription()))
                .collect(Collectors.toList());

        return RetrievalPlan.builder()
                .strategy(RetrievalPlan.RetrievalStrategy.HYBRID)
                .contextTypes(constraints.getContextTypes())
                .retrievalConfigs(configs)
                .topK(10)
                .enableReranking(true)
                .enableDeduplication(true)
                .adjacentChunkMergeCount(2)
                .metadataFilter(RetrievalPlan.MetadataFilter.none())
                .build();
    }
}