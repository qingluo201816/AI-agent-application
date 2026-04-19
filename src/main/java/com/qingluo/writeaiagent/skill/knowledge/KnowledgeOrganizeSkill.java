package com.qingluo.writeaiagent.skill.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.writeaiagent.skill.SkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KnowledgeOrganizeSkill {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识编排专家，负责将小说相关的内容整理成适合 RAG（检索增强生成）入库的标准化 Markdown 文档。

            你的核心职责是：
            1. 分析输入内容，识别其中包含的知识实体（人物、世界观、势力、地点、时间线、事件等）
            2. 按照标准格式重组内容，确保每个知识实体对应一个独立的 entry
            3. 补齐必要的元数据（entry_type、character_name、plot_line 等）
            4. 确保输出的 Markdown 结构兼容现有文档加载器

            输出规范：
            - 使用标准 Markdown 格式
            - ## 用于一级 section（代表知识大类）
            - ### 用于二级 entry（代表具体知识实体）
            - entry 下方使用 `- key: value` 格式书写元数据
            - 正文内容紧跟在元数据之后

            section_type 分类：
            - character: 人物设定
            - relationship: 人物关系
            - worldview: 世界观设定
            - faction: 势力/组织
            - location: 地点/地域
            - power_system: 修炼体系/境界
            - plot: 主线剧情
            - timeline: 时间线
            - artifact: 法宝/功法/血脉
            - mystery: 伏笔/谜团/反转
            - terminology: 名词/术语

            entry_type 推断规则：
            - section_type=character 时，entry_type=character
            - section_type=relationship 时，entry_type=relationship
            - section_type=worldview 时，entry_type=world_rule
            - section_type=plot 时，entry_type=plot_event
            - section_type=timeline 时，entry_type=timeline_event
            - section_type=mystery 时，entry_type=mystery
            - section_type=terminology 时，entry_type=terminology

            元数据字段规范：
            - entry_type: 实体类型（必填）
            - character_name: 角色名（人物类必填）
            - role: 角色定位，如"女主/男主/配角"（人物类建议填写）
            - importance: 重要程度，如"core/major/minor"（人物类建议填写）
            - tags: 标签列表，用逗号分隔（建议填写）
            - subject/object: 关系主体/客体（关系类必填）
            - relation: 关系描述（关系类必填）
            - plot_line: 所属剧情线（剧情类建议填写）
            - timeline_stage: 时间线阶段（时间线类建议填写）
            - spoiler_level: 剧透等级，如"safe/spoiler/critical"（建议填写）

            每个 entry 的正文内容要求：
            - 语义完整，独立可检索
            - 长度适中（建议 200-800 字）
            - 避免多个知识实体混在一个 entry
            - 便于后续 TokenTextSplitter 二次切分
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            请将以下内容整理成标准化的知识文档 Markdown 格式。

            【书籍名称】：{bookName}

            【知识类型】：{knowledgeType}

            【输入内容】：
            {inputContent}

            【整理要求】：
            1. 根据知识类型选择合适的 section 结构
            2. 每个知识实体（角色/势力/事件等）单独作为一个 entry
            3. 为每个 entry 补充必要的元数据
            4. 确保正文物义完整、便于检索
            5. 不要生成冗余的描述性内容，聚焦于结构化知识

            【输出格式】：
            请直接输出 Markdown，不要有其他解释性文字。格式如下：

            ```markdown
            ---
            book_name: {bookName}
            source_type: novel_kb
            doc_type: {docType}
            generated_by: knowledge_skill
            version: v1
            ---

            ## [section标题]

            ### [entry标题]
            - entry_type: [类型]
            - [其他元数据...]

            [entry正文内容...]

            ### [另一个entry]
            - entry_type: [类型]
            - [其他元数据...]

            [entry正文内容...]
            ```
            """;

    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n(.*)");

    private static final Pattern SECTION_PATTERN =
            Pattern.compile("(?m)^##\\s+(.+?)\\s*$");

    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("(?m)^###\\s+(.+?)\\s*$");

    private static final Pattern META_LINE_PATTERN =
            Pattern.compile("(?m)^-\\s*([a-zA-Z0-9_\\-]+)\\s*:\\s*(.*?)\\s*$");

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public KnowledgeOrganizeSkill(@Qualifier("dashscopeChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public SkillDefinition getDefinition() {
        return SkillDefinition.builder()
                .id("knowledge_organize")
                .name("知识编排 Skill")
                .description("将小说内容整理成适合 RAG 入库的标准 Markdown 文档")
                .type(SkillDefinition.SkillType.KNOWLEDGE_ORGANIZE)
                .triggerKeywords(List.of(
                        "整理成可检索", "生成设定文档", "结构化沉淀", "人物设定", "世界观",
                        "时间线", "剧情摘要", "势力设定", "知识沉淀", "沉淀为知识",
                        "整理成知识", "归档", "生成知识文档"
                ))
                .build();
    }

    public KnowledgeOrganizeResult execute(KnowledgeOrganizeInput input) {
        log.info("开始执行知识编排 Skill，输入类型: {}, 内容长度: {}",
                input.getConfig().getPrimaryType(), input.getContent().length());

        try {
            Prompt prompt = buildPrompt(input);
            ChatResponse response = chatModel.call(prompt);

            String markdownContent = extractContent(response);
            log.debug("LLM 返回的 Markdown 内容:\n{}", markdownContent);

            KnowledgeOrganizeResult result = parseMarkdownResult(markdownContent, input);
            result.setRawInput(input.getContent());
            result.setConfig(input.getConfig());
            result.setGeneratedMarkdown(markdownContent);

            validateResult(result);

            log.info("知识编排完成，生成 section 数: {}, entry 数: {}",
                    result.getSections().size(),
                    result.getSections().stream().mapToInt(s -> s.getEntries().size()).sum());

            return result;

        } catch (Exception e) {
            log.error("知识编排执行失败: {}", e.getMessage(), e);
            KnowledgeOrganizeResult errorResult = KnowledgeOrganizeResult.empty();
            errorResult.addWarning("执行失败: " + e.getMessage());
            return errorResult;
        }
    }

    public KnowledgeOrganizeResult executeSimple(String content, String bookName, String knowledgeType) {
        KnowledgeOrganizeConfig.KnowledgeType type;
        try {
            type = KnowledgeOrganizeConfig.KnowledgeType.valueOf(knowledgeType);
        } catch (IllegalArgumentException e) {
            type = KnowledgeOrganizeConfig.KnowledgeType.COMPREHENSIVE;
        }

        KnowledgeOrganizeConfig config = KnowledgeOrganizeConfig.builder()
                .bookName(bookName)
                .primaryType(type)
                .autoEnhanceMetadata(true)
                .build();

        return execute(KnowledgeOrganizeInput.builder()
                .content(content)
                .config(config)
                .build());
    }

    private Prompt buildPrompt(KnowledgeOrganizeInput input) {
        String docType = inferDocType(input.getConfig().getPrimaryType());
        String knowledgeType = input.getConfig().getPrimaryType().getDescription();

        PromptTemplate template = new PromptTemplate(USER_PROMPT_TEMPLATE);
        template.add("bookName", input.getConfig().getBookName() != null ?
                input.getConfig().getBookName() : "未命名书籍");
        template.add("knowledgeType", knowledgeType);
        template.add("docType", docType);
        template.add("inputContent", input.getContent());

        String userText = template.render();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(userText));

        return new Prompt(messages);
    }

    private String inferDocType(KnowledgeOrganizeConfig.KnowledgeType type) {
        return switch (type) {
            case CHARACTER_SHEET -> "character_sheet";
            case WORLDVIEW -> "worldview";
            case FACTION_LOCATION_TERM -> "faction_location_term";
            case TIMELINE -> "timeline";
            case PLOT_SUMMARY -> "plot_summary";
            case MYSTERY_CLUE -> "mystery_clue";
            case RELATIONSHIP -> "relationship";
            case COMPREHENSIVE -> "comprehensive_knowledge";
        };
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }

        String content = response.getResult().getOutput().getText();
        if (content == null) {
            return "";
        }

        content = content.trim();

        if (content.startsWith("```markdown")) {
            content = content.substring("```markdown".length());
        } else if (content.startsWith("```")) {
            content = content.substring(content.indexOf('\n') + 1);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    private KnowledgeOrganizeResult parseMarkdownResult(String markdown, KnowledgeOrganizeInput input) {
        KnowledgeOrganizeResult result = KnowledgeOrganizeResult.empty();

        if (markdown == null || markdown.isBlank()) {
            result.addWarning("LLM 返回内容为空");
            return result;
        }

        Matcher frontMatterMatcher = FRONT_MATTER_PATTERN.matcher(markdown);
        if (frontMatterMatcher.find()) {
            result.setFrontMatter(parseFrontMatter(frontMatterMatcher.group(1)));
            markdown = frontMatterMatcher.group(2);
        } else {
            result.setFrontMatter(buildDefaultFrontMatter(input));
        }

        Matcher sectionMatcher = SECTION_PATTERN.matcher(markdown);
        List<SectionMatch> sectionMatches = new ArrayList<>();
        while (sectionMatcher.find()) {
            sectionMatches.add(new SectionMatch(sectionMatcher.start(), sectionMatcher.end(),
                    sectionMatcher.group(1)));
        }

        for (int i = 0; i < sectionMatches.size(); i++) {
            SectionMatch current = sectionMatches.get(i);
            int bodyStart = current.end;
            int bodyEnd = (i + 1 < sectionMatches.size()) ?
                    sectionMatches.get(i + 1).start : markdown.length();

            String sectionBody = markdown.substring(bodyStart, bodyEnd).trim();
            KnowledgeOrganizeResult.Section section = parseSection(current.title, sectionBody);
            result.getSections().add(section);
        }

        return result;
    }

    private Map<String, Object> parseFrontMatter(String frontMatterText) {
        Map<String, Object> frontMatter = new java.util.LinkedHashMap<>();
        if (frontMatterText == null || frontMatterText.isBlank()) {
            return frontMatter;
        }

        String[] lines = frontMatterText.split("\\R");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                frontMatter.put(key, value);
            }
        }

        return frontMatter;
    }

    private Map<String, Object> buildDefaultFrontMatter(KnowledgeOrganizeInput input) {
        Map<String, Object> frontMatter = new java.util.LinkedHashMap<>();
        frontMatter.put("book_name", input.getConfig().getBookName());
        frontMatter.put("source_type", "novel_kb");
        frontMatter.put("doc_type", inferDocType(input.getConfig().getPrimaryType()));
        frontMatter.put("generated_by", "knowledge_skill");
        frontMatter.put("version", "v1");
        return frontMatter;
    }

    private KnowledgeOrganizeResult.Section parseSection(String title, String body) {
        String sectionType = normalizeSectionType(title);
        KnowledgeOrganizeResult.Section section = KnowledgeOrganizeResult.Section.builder()
                .title(title)
                .sectionType(title)
                .normalizedSectionType(sectionType)
                .entries(new ArrayList<>())
                .build();

        Matcher entryMatcher = ENTRY_PATTERN.matcher(body);
        List<EntryMatch> entryMatches = new ArrayList<>();
        while (entryMatcher.find()) {
            entryMatches.add(new EntryMatch(entryMatcher.start(), entryMatcher.end(),
                    entryMatcher.group(1)));
        }

        if (entryMatches.isEmpty()) {
            section.setBodyContent(body);
            return section;
        }

        for (int i = 0; i < entryMatches.size(); i++) {
            EntryMatch current = entryMatches.get(i);
            int bodyStart = current.end;
            int bodyEnd = (i + 1 < entryMatches.size()) ?
                    entryMatches.get(i + 1).start : body.length();

            String entryBody = body.substring(bodyStart, bodyEnd).trim();
            KnowledgeOrganizeResult.Entry entry = parseEntry(current.title, entryBody, sectionType);
            entry.setEntryOrder(i + 1);
            section.getEntries().add(entry);
        }

        return section;
    }

    private KnowledgeOrganizeResult.Entry parseEntry(String title, String body, String sectionType) {
        KnowledgeOrganizeResult.Entry entry = KnowledgeOrganizeResult.Entry.builder()
                .title(title)
                .metadata(new java.util.LinkedHashMap<>())
                .build();

        if (body == null || body.isBlank()) {
            entry.setEntryType(inferEntryType(sectionType, title, null));
            entry.setContent("");
            return entry;
        }

        String[] lines = body.split("\\R");
        int metaEndIndex = 0;

        while (metaEndIndex < lines.length) {
            Matcher matcher = META_LINE_PATTERN.matcher(lines[metaEndIndex]);
            if (!matcher.matches()) {
                break;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            entry.getMetadata().put(key, value);
            metaEndIndex++;
        }

        while (metaEndIndex < lines.length && lines[metaEndIndex].isBlank()) {
            metaEndIndex++;
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = metaEndIndex; i < lines.length; i++) {
            contentBuilder.append(lines[i]);
            if (i < lines.length - 1) {
                contentBuilder.append("\n");
            }
        }

        entry.setContent(contentBuilder.toString().trim());
        entry.setEntryType(inferEntryType(sectionType, title, entry.getMetadata()));

        return entry;
    }

    private String normalizeSectionType(String title) {
        String t = title == null ? "" : title.replace(" ", "");

        if (t.contains("世界观") || t.contains("设定") || t.contains("规则")) {
            return "worldview";
        }
        if (t.contains("人物关系") || t.contains("关系")) {
            return "relationship";
        }
        if (t.contains("人物") || t.contains("角色")) {
            return "character";
        }
        if (t.contains("地点") || t.contains("地域") || t.contains("场景")) {
            return "location";
        }
        if (t.contains("势力") || t.contains("组织") || t.contains("宗门") || t.contains("阵营")) {
            return "faction";
        }
        if (t.contains("境界") || t.contains("战力") || t.contains("修炼") || t.contains("体系")) {
            return "power_system";
        }
        if (t.contains("情节") || t.contains("剧情") || t.contains("事件") || t.contains("主线")) {
            return "plot";
        }
        if (t.contains("时间线")) {
            return "timeline";
        }
        if (t.contains("功法") || t.contains("法宝") || t.contains("血脉") || t.contains("宝物")) {
            return "artifact";
        }
        if (t.contains("谜团") || t.contains("伏笔") || t.contains("反转")) {
            return "mystery";
        }
        if (t.contains("名词") || t.contains("术语") || t.contains("别名")) {
            return "terminology";
        }
        return "topic";
    }

    private String inferEntryType(String sectionType, String entryTitle, Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("entry_type")) {
            return (String) metadata.get("entry_type");
        }

        return switch (sectionType) {
            case "character" -> "character";
            case "relationship" -> "relationship";
            case "worldview" -> "world_rule";
            case "faction" -> "faction";
            case "location" -> "location";
            case "power_system" -> "power_system";
            case "plot" -> "plot_event";
            case "timeline" -> "timeline_event";
            case "artifact" -> "artifact_or_technique";
            case "mystery" -> "mystery";
            case "terminology" -> "terminology";
            default -> {
                if (entryTitle != null && entryTitle.contains("人物")) {
                    yield "character";
                }
                yield "topic";
            }
        };
    }

    private void validateResult(KnowledgeOrganizeResult result) {
        if (result.getSections().isEmpty()) {
            result.addWarning("没有生成任何 section");
        }

        for (KnowledgeOrganizeResult.Section section : result.getSections()) {
            if (section.getEntries().isEmpty() &&
                    (section.getBodyContent() == null || section.getBodyContent().isBlank())) {
                result.addWarning("Section '" + section.getTitle() + "' 没有内容");
            }
        }

        long totalEntries = result.getSections().stream()
                .mapToInt(s -> s.getEntries().size()).sum();
        if (totalEntries > 50) {
            result.addWarning("entry 数量过多（" + totalEntries + "），建议拆分");
        }
    }

    private record SectionMatch(int start, int end, String title) {}
    private record EntryMatch(int start, int end, String title) {}
}