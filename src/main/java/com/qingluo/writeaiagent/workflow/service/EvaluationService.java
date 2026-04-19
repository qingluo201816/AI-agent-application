package com.qingluo.writeaiagent.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.writeaiagent.workflow.EvaluationResult;
import com.qingluo.writeaiagent.workflow.EvaluationResult.IssueType;
import com.qingluo.writeaiagent.workflow.EvaluationResult.Problem;
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
 * 评估服务
 * 负责对生成结果进行硬约束和软质量检查，决定下一步路径
 */
@Slf4j
@Service
public class EvaluationService {

    private static final String EVALUATION_SYSTEM_PROMPT = """
            你是小说内容质量评估专家，负责对生成的内容进行严格评估。

            评估分为两个维度：
            1. 硬约束检查（必须满足）
            2. 软质量检查（应该满足）

            硬约束检查项：
            - 字数是否在要求范围内
            - POV（视角）是否正确
            - 是否包含必须推进的情节
            - 是否满足输出格式要求
            - 是否包含必须包含的内容
            - 是否避免了必须避免的内容

            软质量检查项：
            - 人物设定是否一致
            - 时间线是否冲突
            - 风格是否贴合
            - 是否真的推进剧情
            - 是否保留悬念
            - 是否提前暴露不该暴露的信息
            - 表达是否自然流畅

            输出格式：
            返回JSON对象，包含：
            - passed: boolean，是否通过评估
            - issueType: "information" | "expression" | "delivery" | "none"，问题类型
            - problems: 数组，每个问题包含 category（问题类别）、severity（严重程度：critical/major/minor）、description（描述）、location（位置）、suggestion（建议）
            - evaluationReport: 详细的评估报告
            """;

    private static final String EVALUATION_USER_PROMPT_TEMPLATE = """
            【任务目标】
            %s

            【字数要求】
            最低%d字，目标%d字，最高%d字
            实际生成内容长度：%d字

            【视角要求】
            %s
            请检查POV是否一致

            【必须包含的内容】
            %s
            请检查这些内容是否都包含

            【必须避免的内容】
            %s
            请检查是否包含了这些应该避免的内容

            【氛围/基调】
            %s
            请检查风格是否贴合

            【生成的内容】
            %s

            【之前评估发现的问题（用于修订后再次评估）】
            %s

            请进行全面的硬约束和软质量检查，并给出评估结果。
            """;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    public EvaluationService(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 执行评估
     * @param content 要评估的内容
     * @param context 任务上下文
     * @return 评估结果
     */
    public EvaluationResult evaluate(String content, TaskContext context) {
        log.info("开始评估: taskId={}, contentLength={}", context.getTaskId(), content.length());

        String prompt = buildEvaluationPrompt(content, context);
        EvaluationResult result = callLlmForEvaluation(prompt, content, context);

        context.setEvaluationResult(result);
        context.setCurrentStage(WorkflowStage.EVALUATE);
        
        // 构建详细的评估日志
        StringBuilder logMsg = new StringBuilder();
        logMsg.append(String.format("评估完成: passed=%s, issueType=%s, problems=%d",
                result.isPassed(), result.getIssueType(), result.getProblems().size()));
        
        if (!result.isPassed()) {
            logMsg.append("\n详细问题:");
            for (EvaluationResult.Problem problem : result.getProblems()) {
                logMsg.append(String.format("\n- %s (%s): %s", 
                        problem.getCategory().getDescription(), 
                        problem.getSeverity().getDescription(),
                        problem.getDescription()));
            }
            
            // 输出缺失的上下文类型
            if (result.getMissingContextTypes() != null && !result.getMissingContextTypes().isEmpty()) {
                logMsg.append("\n缺失的上下文类型: " + String.join(", ", result.getMissingContextTypes()));
            }
        }
        
        context.addExecutionLog(WorkflowStage.EVALUATE, "EVALUATE", logMsg.toString(), true);

        log.info("评估完成: taskId={}, passed={}, issueType={}, problems={}, missingContextTypes={}",
                context.getTaskId(), result.isPassed(), result.getIssueType(), 
                result.getProblems().size(), 
                result.getMissingContextTypes() != null ? String.join(", ", result.getMissingContextTypes()) : "无");

        return result;
    }

    /**
     * 快速检查（用于生成后立即判断是否需要重试）
     */
    public boolean quickCheck(String content, TaskContext context) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        TaskConstraints constraints = context.getConstraints();
        int contentLength = content.length();

        if (contentLength < constraints.getWordCount().getMin() * 0.5) {
            log.warn("字数严重不足: 需要{}字，实际{}字", constraints.getWordCount().getMin(), contentLength);
            return false;
        }

        if (constraints.getMustInclude() != null && !constraints.getMustInclude().isEmpty()) {
            for (String mustInclude : constraints.getMustInclude()) {
                if (!content.contains(mustInclude)) {
                    log.warn("缺少必须包含的内容: {}", mustInclude);
                    return false;
                }
            }
        }

        return true;
    }

    private String buildEvaluationPrompt(String content, TaskContext context) {
        TaskConstraints constraints = context.getConstraints();

        String mustInclude = constraints.getMustInclude() != null && !constraints.getMustInclude().isEmpty() ?
                String.join("、", constraints.getMustInclude()) : "无";

        String mustAvoid = constraints.getMustAvoid() != null && !constraints.getMustAvoid().isEmpty() ?
                String.join("、", constraints.getMustAvoid()) : "无";

        String previousProblems = "";
        if (context.getEvaluationResult() != null && !context.getEvaluationResult().isPassed()) {
            previousProblems = context.getEvaluationResult().getProblems().stream()
                    .map(p -> String.format("- [%s] %s (建议: %s)",
                            p.getSeverity(), p.getDescription(), p.getSuggestion()))
                    .collect(Collectors.joining("\n"));
        }
        if (previousProblems.isEmpty()) {
            previousProblems = "无（首次评估）";
        }

        String contentPreview = content.length() > 3000 ? content.substring(0, 3000) + "..." : content;

        return String.format(EVALUATION_USER_PROMPT_TEMPLATE,
                constraints.getGoalDescription(),
                constraints.getWordCount().getMin(),
                constraints.getWordCount().getTarget(),
                constraints.getWordCount().getMax(),
                content.length(),
                constraints.getPov(),
                mustInclude,
                mustAvoid,
                constraints.getTone(),
                contentPreview,
                previousProblems
        );
    }

    private EvaluationResult callLlmForEvaluation(String prompt, String content, TaskContext context) {
        try {
            AssistantMessage response = chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage(EVALUATION_SYSTEM_PROMPT),
                            new UserMessage(prompt)
                    ))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput();

            String jsonStr = response.getText();
            log.debug("评估LLM原始输出: {}", jsonStr);
            return parseEvaluationResult(jsonStr, content, context);
        } catch (Exception e) {
            log.error("评估LLM调用失败: {}", e.getMessage());
            return EvaluationResult.fail(IssueType.EXPRESSION,
                    List.of(Problem.builder()
                            .category(Problem.ProblemCategory.PACING_ISSUE)
                            .severity(Problem.ProblemSeverity.MAJOR)
                            .description("评估过程出错")
                            .suggestion("请人工检查内容质量")
                            .build()),
                    "评估失败: " + e.getMessage());
        }
    }

    private EvaluationResult parseEvaluationResult(String jsonStr, String content, TaskContext context) {
        try {
            String cleanedJson = cleanJsonString(jsonStr);
            JsonNode root = objectMapper.readTree(cleanedJson);

            boolean passed = root.path("passed").asBoolean(false);
            String issueTypeStr = root.path("issueType").asText("none");
            IssueType issueType = parseIssueType(issueTypeStr);
            String report = root.path("evaluationReport").asText("评估完成");

            List<Problem> problems = new ArrayList<>();
            JsonNode problemsNode = root.path("problems");
            if (problemsNode.isArray()) {
                for (JsonNode problemNode : problemsNode) {
                    problems.add(parseProblem(problemNode));
                }
            }

            // 解析缺失的上下文类型
            List<String> missingContextTypes = new ArrayList<>();
            JsonNode missingContextNode = root.path("missingContextTypes");
            if (missingContextNode.isArray()) {
                for (JsonNode typeNode : missingContextNode) {
                    missingContextTypes.add(typeNode.asText());
                }
            }

            // 解析建议的检索调整
            EvaluationResult.SuggestedRetrievalAdjustments suggestedRetrievalAdjustments = null;
            JsonNode retrievalAdjustmentsNode = root.path("suggestedRetrievalAdjustments");
            if (!retrievalAdjustmentsNode.isMissingNode()) {
                var adjustmentsBuilder = EvaluationResult.SuggestedRetrievalAdjustments.builder();
                
                if (retrievalAdjustmentsNode.has("expandChapterRange")) {
                    adjustmentsBuilder.expandChapterRange(retrievalAdjustmentsNode.path("expandChapterRange").asBoolean());
                }
                if (retrievalAdjustmentsNode.has("relaxMetadataFilter")) {
                    adjustmentsBuilder.relaxMetadataFilter(retrievalAdjustmentsNode.path("relaxMetadataFilter").asBoolean());
                }
                if (retrievalAdjustmentsNode.has("increaseTopK")) {
                    adjustmentsBuilder.increaseTopK(retrievalAdjustmentsNode.path("increaseTopK").asInt());
                }
                if (retrievalAdjustmentsNode.has("suggestedAdditionalContextTypes")) {
                    List<String> additionalContextTypes = new ArrayList<>();
                    JsonNode typesNode = retrievalAdjustmentsNode.path("suggestedAdditionalContextTypes");
                    if (typesNode.isArray()) {
                        for (JsonNode typeNode : typesNode) {
                            additionalContextTypes.add(typeNode.asText());
                        }
                    }
                    adjustmentsBuilder.suggestedAdditionalContextTypes(additionalContextTypes);
                }
                if (retrievalAdjustmentsNode.has("suggestedQueryKeywords")) {
                    List<String> queryKeywords = new ArrayList<>();
                    JsonNode keywordsNode = retrievalAdjustmentsNode.path("suggestedQueryKeywords");
                    if (keywordsNode.isArray()) {
                        for (JsonNode keywordNode : keywordsNode) {
                            queryKeywords.add(keywordNode.asText());
                        }
                    }
                    adjustmentsBuilder.suggestedQueryKeywords(queryKeywords);
                }
                if (retrievalAdjustmentsNode.has("suggestedRetrievalStrategy")) {
                    adjustmentsBuilder.suggestedRetrievalStrategy(retrievalAdjustmentsNode.path("suggestedRetrievalStrategy").asText());
                }
                
                suggestedRetrievalAdjustments = adjustmentsBuilder.build();
            }

            // 解析建议的生成修复
            EvaluationResult.SuggestedGenerationFixes suggestedGenerationFixes = null;
            JsonNode generationFixesNode = root.path("suggestedGenerationFixes");
            if (!generationFixesNode.isMissingNode()) {
                var fixesBuilder = EvaluationResult.SuggestedGenerationFixes.builder();
                
                if (generationFixesNode.has("needLocalRewrite")) {
                    fixesBuilder.needLocalRewrite(generationFixesNode.path("needLocalRewrite").asBoolean());
                }
                if (generationFixesNode.has("rewriteScope")) {
                    fixesBuilder.rewriteScope(generationFixesNode.path("rewriteScope").asText());
                }
                if (generationFixesNode.has("fixDirections")) {
                    List<String> fixDirections = new ArrayList<>();
                    JsonNode directionsNode = generationFixesNode.path("fixDirections");
                    if (directionsNode.isArray()) {
                        for (JsonNode directionNode : directionsNode) {
                            fixDirections.add(directionNode.asText());
                        }
                    }
                    fixesBuilder.fixDirections(fixDirections);
                }
                if (generationFixesNode.has("styleAdjustment")) {
                    fixesBuilder.styleAdjustment(generationFixesNode.path("styleAdjustment").asText());
                }
                if (generationFixesNode.has("povAdjustment")) {
                    fixesBuilder.povAdjustment(generationFixesNode.path("povAdjustment").asText());
                }
                if (generationFixesNode.has("lengthAdjustment")) {
                    fixesBuilder.lengthAdjustment(generationFixesNode.path("lengthAdjustment").asText());
                }
                
                suggestedGenerationFixes = fixesBuilder.build();
            }

            if (passed) {
                return EvaluationResult.pass();
            } else {
                return EvaluationResult.fail(issueType, problems, report, missingContextTypes, 
                        suggestedRetrievalAdjustments, suggestedGenerationFixes);
            }
        } catch (Exception e) {
            log.warn("评估结果JSON解析失败: {}", e.getMessage());
            return fallbackEvaluation(content, context);
        }
    }

    private String cleanJsonString(String jsonStr) {
        String cleaned = jsonStr.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private IssueType parseIssueType(String typeStr) {
        return switch (typeStr.toLowerCase()) {
            case "information" -> IssueType.INFORMATION;
            case "expression" -> IssueType.EXPRESSION;
            case "delivery" -> IssueType.DELIVERY;
            default -> IssueType.NONE;
        };
    }

    private Problem parseProblem(JsonNode node) {
        String categoryStr = node.path("category").asText("PACING_ISSUE");
        Problem.ProblemCategory category;
        try {
            category = Problem.ProblemCategory.valueOf(categoryStr.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            category = Problem.ProblemCategory.PACING_ISSUE;
        }

        String severityStr = node.path("severity").asText("major");
        Problem.ProblemSeverity severity;
        try {
            severity = Problem.ProblemSeverity.valueOf(severityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            severity = Problem.ProblemSeverity.MAJOR;
        }

        return Problem.builder()
                .category(category)
                .severity(severity)
                .description(node.path("description").asText(""))
                .location(node.path("location").asText(""))
                .suggestion(node.path("suggestion").asText(""))
                .build();
    }

    private EvaluationResult fallbackEvaluation(String content, TaskContext context) {
        TaskConstraints constraints = context.getConstraints();
        List<Problem> problems = new ArrayList<>();
        int contentLength = content.length();

        if (contentLength < constraints.getWordCount().getMin()) {
            problems.add(Problem.builder()
                    .category(Problem.ProblemCategory.WORD_COUNT)
                    .severity(Problem.ProblemSeverity.MAJOR)
                    .description(String.format("字数不足：需要至少%d字，实际%d字",
                            constraints.getWordCount().getMin(), contentLength))
                    .suggestion("请扩展内容以满足字数要求")
                    .build());
        } else if (contentLength > constraints.getWordCount().getMax()) {
            problems.add(Problem.builder()
                    .category(Problem.ProblemCategory.WORD_COUNT)
                    .severity(Problem.ProblemSeverity.MINOR)
                    .description(String.format("字数超出上限：限制%d字，实际%d字",
                            constraints.getWordCount().getMax(), contentLength))
                    .suggestion("请适当精简内容")
                    .build());
        }

        if (problems.isEmpty()) {
            return EvaluationResult.pass();
        }

        IssueType issueType = determineIssueType(problems);
        
        // 为 fallback 评估添加默认的调整建议
        List<String> missingContextTypes = new ArrayList<>();
        if (issueType == IssueType.INFORMATION) {
            missingContextTypes.add("recent_chapters");
            missingContextTypes.add("character_profiles");
        }
        
        EvaluationResult.SuggestedRetrievalAdjustments adjustments = EvaluationResult.SuggestedRetrievalAdjustments.builder()
                .expandChapterRange(true)
                .increaseTopK(15)
                .build();
        
        EvaluationResult.SuggestedGenerationFixes fixes = EvaluationResult.SuggestedGenerationFixes.builder()
                .fixDirections(List.of("检查并修正字数", "确保内容符合要求"))
                .build();
        
        return EvaluationResult.fail(issueType, problems, "快速评估发现问题", 
                missingContextTypes, adjustments, fixes);
    }

    private IssueType determineIssueType(List<Problem> problems) {
        for (Problem problem : problems) {
            switch (problem.getCategory()) {
                case WORD_COUNT:
                case MISSING_CONTENT:
                    return IssueType.INFORMATION;
                case POV_VIOLATION:
                case STYLE_MISMATCH:
                case TONE_MISMATCH:
                case REPETITION:
                case PACING_ISSUE:
                    return IssueType.EXPRESSION;
                case PDF_GENERATION_FAILED:
                case FILE_FORMAT_ERROR:
                    return IssueType.DELIVERY;
                default:
                    break;
            }
        }
        return IssueType.EXPRESSION;
    }

    /**
     * 构建问题摘要（用于反馈给生成阶段）
     */
    public String buildRevisionFeedback(EvaluationResult result) {
        if (result.isPassed()) {
            return "内容已通过评估";
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append("【评估发现的问题】\n\n");

        List<Problem> critical = result.getProblems().stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.CRITICAL)
                .toList();
        List<Problem> major = result.getProblems().stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.MAJOR)
                .toList();
        List<Problem> minor = result.getProblems().stream()
                .filter(p -> p.getSeverity() == Problem.ProblemSeverity.MINOR)
                .toList();

        if (!critical.isEmpty()) {
            feedback.append("【严重问题】\n");
            critical.forEach(p -> feedback.append("- ").append(p.getDescription()).append("\n"));
            feedback.append("\n");
        }

        if (!major.isEmpty()) {
            feedback.append("【重要问题】\n");
            major.forEach(p -> feedback.append("- ").append(p.getDescription()));
            if (major.get(0).getSuggestion() != null) {
                feedback.append(" (建议: ").append(major.get(0).getSuggestion()).append(")");
            }
            feedback.append("\n");
        }

        if (!minor.isEmpty()) {
            feedback.append("【轻微问题】\n");
            minor.forEach(p -> feedback.append("- ").append(p.getDescription()).append("\n"));
        }

        feedback.append("\n【下一步建议】\n");
        feedback.append("请根据以上问题进行修订，优先解决严重问题和重要问题。\n");

        return feedback.toString();
    }
}