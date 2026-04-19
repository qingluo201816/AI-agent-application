package com.qingluo.writeaiagent.workflow.service;

import com.qingluo.writeaiagent.workflow.EvaluationResult;
import com.qingluo.writeaiagent.workflow.RetrievalPlan;
import com.qingluo.writeaiagent.workflow.TaskConstraints;
import com.qingluo.writeaiagent.workflow.TaskContext;
import com.qingluo.writeaiagent.workflow.TaskContext.RetrievedChunk;
import com.qingluo.writeaiagent.workflow.WorkflowStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 检索服务
 * 负责根据任务类型和约束条件执行知识检索
 */
@Slf4j
@Service
public class RetrievalService {

    private static final String CONTEXT_TYPE_SEARCH_PROMPT = """
            根据以下上下文类型和查询，生成适合向量检索的搜索查询。

            上下文类型: %s
            用户任务: %s

            请生成3-5个搜索查询，用于从向量数据库中检索相关内容。
            每个查询应该：
            1. 聚焦于特定的上下文类型
            2. 使用中文关键词
            3. 简洁明了

            输出格式：
            只输出JSON数组，不要有其他文字。例如：["查询1", "查询2", "查询3"]
            """;

    private static final String RERANKING_PROMPT = """
            给定一个用户任务和多个检索到的文档片段，请评估每个片段与任务的相关程度。

            用户任务：%s
            检索到的片段：
            %s

            请评估每个片段的相关性，考虑：
            1. 内容与任务的匹配程度
            2. 信息的准确性和可靠性
            3. 上下文的连贯性

            输出格式：
            返回JSON数组，每个元素包含：
            - index: 片段索引
            - score: 相关性分数 (0.0-1.0)
            - reason: 评分理由

            例如：[{"index": 0, "score": 0.9, "reason": "直接相关"}]
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RetrievalService(VectorStore writeAppVectorStore, ChatModel dashscopeChatModel) {
        this.vectorStore = writeAppVectorStore;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 执行检索
     * @param context 任务上下文
     * @return 检索后的任务上下文（更新了retrievedChunks）
     */
    public TaskContext retrieve(TaskContext context) {
        log.info("开始检索阶段: taskId={}, retrievalRetry={}", context.getTaskId(), context.getRetrievalRetryCount());

        // 检查是否有评估结果，基于评估结果调整检索策略
        adjustRetrievalPlanBasedOnEvaluation(context);

        RetrievalPlan retrievalPlan = context.getRetrievalPlan();
        if (retrievalPlan == null) {
            log.warn("检索计划为空，使用默认检索");
            retrievalPlan = RetrievalPlan.defaultPlan(context.getContextTypes());
            context.setRetrievalPlan(retrievalPlan);
        }

        // 记录检索计划详情
        log.info("执行检索计划: strategy={}, topK={}, dedup={}, rerank={}, merge={}",
                retrievalPlan.getStrategy(),
                retrievalPlan.getTopK(),
                retrievalPlan.isEnableDeduplication(),
                retrievalPlan.isEnableReranking(),
                retrievalPlan.getAdjacentChunkMergeCount());

        List<String> searchQueries = generateSearchQueries(context);
        log.info("生成搜索查询: {}", searchQueries);

        List<RetrievedChunk> allChunks = new ArrayList<>();

        for (String query : searchQueries) {
            try {
                List<RetrievedChunk> chunks = performVectorSearch(query, retrievalPlan.getTopK(), context);
                allChunks.addAll(chunks);
                log.debug("查询 '{}' 召回 {} 个Chunk", query, chunks.size());
            } catch (Exception e) {
                log.error("向量检索失败: query={}, error={}", query, e.getMessage());
            }
        }

        if (retrievalPlan.isEnableDeduplication()) {
            int beforeDedup = allChunks.size();
            allChunks = deduplicateChunks(allChunks);
            log.info("去重前 {} 个，去重后 {} 个", beforeDedup, allChunks.size());
        }

        if (retrievalPlan.isEnableReranking()) {
            log.info("开始重排序，{} 个Chunk", allChunks.size());
            allChunks = rerankChunks(allChunks, context);
        }

        if (retrievalPlan.getAdjacentChunkMergeCount() > 0) {
            int beforeMerge = allChunks.size();
            allChunks = mergeAdjacentChunks(allChunks, retrievalPlan.getAdjacentChunkMergeCount());
            log.info("合并前 {} 个，合并后 {} 个", beforeMerge, allChunks.size());
        }

        context.setRetrievedChunks(allChunks);
        context.setCurrentStage(WorkflowStage.RETRIEVE);
        context.addExecutionLog(WorkflowStage.RETRIEVE, "RETRIEVE",
                String.format("检索完成，召回%d个Chunk", allChunks.size()), true);

        log.info("检索完成: taskId={}, chunks={}", context.getTaskId(), allChunks.size());
        return context;
    }

    /**
     * 基于评估结果调整检索计划
     */
    private void adjustRetrievalPlanBasedOnEvaluation(TaskContext context) {
        EvaluationResult evaluationResult = context.getEvaluationResult();
        if (evaluationResult == null || evaluationResult.isPassed()) {
            return;
        }

        RetrievalPlan currentPlan = context.getRetrievalPlan();
        if (currentPlan == null) {
            currentPlan = RetrievalPlan.defaultPlan(context.getContextTypes());
        }

        // 深拷贝原始计划以便比较差异
        RetrievalPlan originalPlan = RetrievalPlan.builder()
                .strategy(currentPlan.getStrategy())
                .topK(currentPlan.getTopK())
                .enableDeduplication(currentPlan.isEnableDeduplication())
                .enableReranking(currentPlan.isEnableReranking())
                .adjacentChunkMergeCount(currentPlan.getAdjacentChunkMergeCount())
                .build();

        // 基于评估结果调整
        EvaluationResult.SuggestedRetrievalAdjustments adjustments = evaluationResult.getSuggestedRetrievalAdjustments();
        List<String> missingContextTypes = evaluationResult.getMissingContextTypes();

        log.info("基于评估结果调整检索计划: issueType={}, missingContextTypes={}, retrievalRetry={}, revisionCount={}", 
                evaluationResult.getIssueType(), 
                missingContextTypes != null ? String.join(", ", missingContextTypes) : "无",
                context.getRetrievalRetryCount(),
                context.getRevisionCount());

        if (adjustments != null) {
            // 调整 topK
            if (adjustments.getIncreaseTopK() != null) {
                currentPlan.setTopK(Math.max(currentPlan.getTopK(), adjustments.getIncreaseTopK()));
                log.info("调整 topK 为 {}", currentPlan.getTopK());
            }

            // 调整策略
            if (adjustments.getSuggestedRetrievalStrategy() != null) {
                try {
                    RetrievalPlan.RetrievalStrategy strategy = RetrievalPlan.RetrievalStrategy.valueOf(
                            adjustments.getSuggestedRetrievalStrategy().toUpperCase());
                    currentPlan.setStrategy(strategy);
                    log.info("调整检索策略为 {}", currentPlan.getStrategy());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的检索策略: {}", adjustments.getSuggestedRetrievalStrategy());
                }
            }

            // 添加建议的上下文类型
            if (adjustments.getSuggestedAdditionalContextTypes() != null && !adjustments.getSuggestedAdditionalContextTypes().isEmpty()) {
                List<TaskConstraints.ContextType> currentContextTypes = context.getContextTypes();
                for (String typeStr : adjustments.getSuggestedAdditionalContextTypes()) {
                    try {
                        TaskConstraints.ContextType type = TaskConstraints.ContextType.valueOf(typeStr.toUpperCase());
                        if (!currentContextTypes.contains(type)) {
                            currentContextTypes.add(type);
                            log.info("添加上下文类型: {}", type);
                        }
                    } catch (Exception e) {
                        log.warn("未知的上下文类型: {}", typeStr);
                    }
                }
            }
        }

        // 添加缺失的上下文类型
        if (missingContextTypes != null && !missingContextTypes.isEmpty()) {
            List<TaskConstraints.ContextType> currentContextTypes = context.getContextTypes();
            for (String typeStr : missingContextTypes) {
                try {
                    TaskConstraints.ContextType type = TaskConstraints.ContextType.valueOf(typeStr.toUpperCase());
                    if (!currentContextTypes.contains(type)) {
                        currentContextTypes.add(type);
                        log.info("添加缺失的上下文类型: {}", type);
                    }
                } catch (Exception e) {
                    log.warn("未知的上下文类型: {}", typeStr);
                }
            }
        }

        // 确保检索计划与上一轮不同
        if (isPlanSameAsPrevious(context, currentPlan)) {
            // 如果相同，强制调整一些参数
            currentPlan.setTopK(currentPlan.getTopK() + 3);
            currentPlan.setStrategy(currentPlan.getStrategy() == RetrievalPlan.RetrievalStrategy.HYBRID ? 
                    RetrievalPlan.RetrievalStrategy.BROAD : RetrievalPlan.RetrievalStrategy.HYBRID);
            log.info("检索计划与上一轮相同，强制调整: topK={}, strategy={}", currentPlan.getTopK(), currentPlan.getStrategy());
        }

        // 即使没有评估调整建议，也基于 issueType 进行调整
        if (evaluationResult != null && evaluationResult.getIssueType() == EvaluationResult.IssueType.INFORMATION) {
            // 信息层问题，增加 topK 和调整策略
            currentPlan.setTopK(Math.min(currentPlan.getTopK() + 2, 20)); // 最多20
            log.info("基于 INFORMATION 类型问题，增加 topK 为 {}", currentPlan.getTopK());
        }

        context.setRetrievalPlan(currentPlan);
        
        // 记录计划差异
        log.info("检索计划调整完成: {}", getPlanDifference(originalPlan, currentPlan));
    }

    /**
     * 检查当前计划是否与上一轮相同
     */
    private boolean isPlanSameAsPrevious(TaskContext context, RetrievalPlan currentPlan) {
        // 从上下文获取上一轮的检索计划
        RetrievalPlan previousPlan = context.getRetrievalPlan();
        if (previousPlan == null) {
            return false;
        }
        
        // 比较关键参数
        return previousPlan.getStrategy() == currentPlan.getStrategy() &&
               previousPlan.getTopK() == currentPlan.getTopK() &&
               previousPlan.isEnableDeduplication() == currentPlan.isEnableDeduplication() &&
               previousPlan.isEnableReranking() == currentPlan.isEnableReranking();
    }

    /**
     * 获取计划差异
     */
    private String getPlanDifference(RetrievalPlan original, RetrievalPlan current) {
        List<String> differences = new ArrayList<>();
        if (!original.getStrategy().equals(current.getStrategy())) {
            differences.add("策略: " + original.getStrategy() + " -> " + current.getStrategy());
        }
        if (original.getTopK() != current.getTopK()) {
            differences.add("topK: " + original.getTopK() + " -> " + current.getTopK());
        }
        if (original.isEnableDeduplication() != current.isEnableDeduplication()) {
            differences.add("去重: " + original.isEnableDeduplication() + " -> " + current.isEnableDeduplication());
        }
        if (original.isEnableReranking() != current.isEnableReranking()) {
            differences.add("重排序: " + original.isEnableReranking() + " -> " + current.isEnableReranking());
        }
        if (original.getAdjacentChunkMergeCount() != current.getAdjacentChunkMergeCount()) {
            differences.add("合并数: " + original.getAdjacentChunkMergeCount() + " -> " + current.getAdjacentChunkMergeCount());
        }
        return differences.isEmpty() ? "无变化" : String.join(", ", differences);
    }

    /**
     * 重新检索（用于检索失败后重试）
     */
    public TaskContext reRetrieve(TaskContext context) {
        // 这里不再增加检索重试计数，因为 WorkflowEngine 已经处理了
        // context.incrementRetrievalRetry();
        context.setRetrievedChunks(new ArrayList<>());
        log.info("执行重新检索: taskId={}, current retrievalRetryCount={}", 
                context.getTaskId(), context.getRetrievalRetryCount());
        return retrieve(context);
    }

    private List<String> generateSearchQueries(TaskContext context) {
        List<String> queries = new ArrayList<>();

        String contextTypesStr = context.getContextTypes().stream()
                .map(TaskConstraints.ContextType::getDescription)
                .collect(Collectors.joining(", "));

        String prompt = String.format(CONTEXT_TYPE_SEARCH_PROMPT, contextTypesStr, context.getConstraints().getGoalDescription());

        try {
            AssistantMessage response = chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage("你是搜索查询生成专家"),
                            new UserMessage(prompt)
                    ))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput();

            String queryJson = response.getText();
            queries = parseQueryJson(queryJson);
        } catch (Exception e) {
            log.error("生成搜索查询失败: {}", e.getMessage());
        }

        if (queries.isEmpty()) {
            queries.add(context.getConstraints().getGoalDescription());
            if (context.getConstraints().getMustInclude() != null) {
                context.getConstraints().getMustInclude().forEach(queries::add);
            }
        }

        return queries;
    }

    private List<String> parseQueryJson(String jsonStr) {
        List<String> queries = new ArrayList<>();
        try {
            String cleanedJson = jsonStr.trim();
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7);
            }
            if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3);
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            }
            cleanedJson = cleanedJson.trim();

            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanedJson);

            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    queries.add(node.asText());
                }
            }
        } catch (Exception e) {
            log.warn("解析查询JSON失败: {}", e.getMessage());
        }
        return queries;
    }

    private List<RetrievedChunk> performVectorSearch(String query, int topK, TaskContext context) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        return documents.stream()
                .map(doc -> convertToChunk(doc, query, context))
                .collect(Collectors.toList());
    }

    private RetrievedChunk convertToChunk(Document doc, String query, TaskContext context) {
        Map<String, Object> metadata = doc.getMetadata();
        double score = 0.0;
        if (metadata.containsKey("score")) {
            score = Double.parseDouble(metadata.get("score").toString());
        }

        TaskConstraints.ContextType contextType = inferContextType(doc.getText(), context);

        return RetrievedChunk.builder()
                .chunkId(UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .content(doc.getText())
                .similarityScore(score)
                .contextType(contextType)
                .metadata(metadata)
                .sourceDocument(metadata.getOrDefault("source", "unknown").toString())
                .chapterNumber(parseChapterNumber(metadata))
                .isUsed(true)
                .build();
    }

    private TaskConstraints.ContextType inferContextType(String text, TaskContext context) {
        String lowerText = text.toLowerCase();
        String taskType = context.getTaskType() != null ? context.getTaskType().name() : "";

        if (taskType.contains("CHARACTER") || lowerText.contains("角色") || lowerText.contains("人物")) {
            return TaskConstraints.ContextType.CHARACTER_PROFILES;
        }
        if (taskType.contains("PLOT") || lowerText.contains("情节") || lowerText.contains("剧情")) {
            return TaskConstraints.ContextType.ACTIVE_PLOTLINE;
        }
        if (taskType.contains("TIMELINE") || lowerText.contains("时间")) {
            return TaskConstraints.ContextType.TIMELINE;
        }
        if (taskType.contains("WORLD") || lowerText.contains("世界观") || lowerText.contains("设定")) {
            return TaskConstraints.ContextType.WORLD_SETTING;
        }

        return TaskConstraints.ContextType.RECENT_CHAPTERS;
    }

    private int parseChapterNumber(Map<String, Object> metadata) {
        Object chapter = metadata.get("chapter");
        if (chapter != null) {
            try {
                return Integer.parseInt(chapter.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private List<RetrievedChunk> deduplicateChunks(List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> uniqueChunks = new LinkedHashMap<>();

        for (RetrievedChunk chunk : chunks) {
            String contentPreview = chunk.getContent().substring(0, Math.min(100, chunk.getContent().length()));
            uniqueChunks.putIfAbsent(contentPreview, chunk);
        }

        return new ArrayList<>(uniqueChunks.values());
    }

    private List<RetrievedChunk> rerankChunks(List<RetrievedChunk> chunks, TaskContext context) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        String chunksText = IntStream.range(0, chunks.size())
                .mapToObj(i -> String.format("[%d] %s", i,
                        chunks.get(i).getContent().substring(0, Math.min(200, chunks.get(i).getContent().length()))))
                .collect(Collectors.joining("\n---\n"));

        String prompt = String.format(RERANKING_PROMPT, context.getConstraints().getGoalDescription(), chunksText);

        try {
            AssistantMessage response = chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage("你是一个专业的文档相关性评估专家"),
                            new UserMessage(prompt)
                    ))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput();

            Map<Integer, Double> scores = parseRerankingResponse(response.getText(), chunks.size());

            return chunks.stream()
                    .peek(chunk -> {
                        int index = chunks.indexOf(chunk);
                        if (scores.containsKey(index)) {
                            chunk.setSimilarityScore(scores.get(index));
                        }
                    })
                    .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("重排序失败: {}", e.getMessage());
            return chunks;
        }
    }

    private Map<Integer, Double> parseRerankingResponse(String jsonStr, int maxSize) {
        Map<Integer, Double> scores = new HashMap<>();
        try {
            // 清洗 markdown code fence
            String cleanJsonStr = cleanMarkdownCodeFence(jsonStr);
            
            com.fasterxml.jackson.databind.JsonNode root = 
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanJsonStr);
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    int index = node.path("index").asInt(0);
                    double score = node.path("score").asDouble(0.5);
                    if (index >= 0 && index < maxSize) {
                        scores.put(index, score);
                    }
                }
                log.debug("重排序结果解析成功，解析到 {} 个分数", scores.size());
            } else {
                log.warn("重排序结果不是数组格式，使用原始相似度分数");
                return generateFallbackScores(maxSize);
            }
        } catch (Exception e) {
            log.warn("解析重排序结果失败: {}, 使用降级策略", e.getMessage());
            return generateFallbackScores(maxSize);
        }
        
        if (scores.isEmpty()) {
            log.warn("重排序结果为空，使用降级策略");
            return generateFallbackScores(maxSize);
        }
        
        return scores;
    }
    
    /**
     * 清洗 markdown code fence
     */
    private String cleanMarkdownCodeFence(String text) {
        // 移除 ```json 和 ``` 包裹
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        // 去除首尾空白
        return text.trim();
    }
    
    /**
     * 生成降级分数（线性递减）
     */
    private Map<Integer, Double> generateFallbackScores(int size) {
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < size; i++) {
            // 线性递减的分数，从 0.9 到 0.1
            double score = 0.9 - (i * 0.8 / Math.max(1, size - 1));
            scores.put(i, score);
        }
        log.debug("使用降级策略生成分数: {}", scores);
        return scores;
    }

    private List<RetrievedChunk> mergeAdjacentChunks(List<RetrievedChunk> chunks, int mergeCount) {
        if (chunks.size() <= mergeCount) {
            return chunks;
        }

        List<RetrievedChunk> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        RetrievedChunk firstChunk = chunks.get(0);

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            buffer.append(chunk.getContent()).append("\n\n");

            if ((i + 1) % mergeCount == 0 || i == chunks.size() - 1) {
                merged.add(RetrievedChunk.builder()
                        .chunkId(firstChunk.getChunkId() + "_m" + (merged.size() + 1))
                        .content(buffer.toString().trim())
                        .similarityScore(chunk.getSimilarityScore())
                        .contextType(chunk.getContextType())
                        .metadata(firstChunk.getMetadata())
                        .sourceDocument(firstChunk.getSourceDocument())
                        .chapterNumber(firstChunk.getChapterNumber())
                        .isUsed(true)
                        .build());
                buffer.setLength(0);
                if (i < chunks.size() - 1) {
                    firstChunk = chunks.get(i + 1);
                }
            }
        }

        return merged;
    }

    /**
     * 构建检索上下文摘要（用于传递给生成阶段）
     */
    public String buildRetrievalContext(TaskContext context) {
        List<RetrievedChunk> chunks = context.getRetrievedChunks();
        if (chunks == null || chunks.isEmpty()) {
            return "未检索到相关上下文。";
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("【检索到的相关上下文】\n\n");

        Map<TaskConstraints.ContextType, List<RetrievedChunk>> chunksByType = chunks.stream()
                .collect(Collectors.groupingBy(RetrievedChunk::getContextType));

        for (Map.Entry<TaskConstraints.ContextType, List<RetrievedChunk>> entry : chunksByType.entrySet()) {
            contextBuilder.append("【").append(entry.getKey().getDescription()).append("】\n");
            for (RetrievedChunk chunk : entry.getValue()) {
                String content = chunk.getContent();
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...(省略)";
                }
                contextBuilder.append(content).append("\n\n");
            }
            contextBuilder.append("---\n\n");
        }

        return contextBuilder.toString();
    }
}