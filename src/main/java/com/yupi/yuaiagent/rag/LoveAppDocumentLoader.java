//package com.yupi.yuaiagent.rag;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
//import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.support.ResourcePatternResolver;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * AI 写作智能体应用文档加载器
// */
//@Component
//@Slf4j
//public class LoveAppDocumentLoader {
//
//    private final ResourcePatternResolver resourcePatternResolver;
//
//    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
//        this.resourcePatternResolver = resourcePatternResolver;
//    }
//
//    /**
//     * 加载多篇 Markdown 文档
//     * @return
//     */
//    public List<Document> loadMarkdowns() {
//        List<Document> allDocuments = new ArrayList<>();
//        try {
//            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
//            for (Resource resource : resources) {
//                String filename = resource.getFilename();
//                // 提取文档倒数第 3 和第 2 个字作为标签
//                String status = filename.substring(filename.length() - 6, filename.length() - 4);
//                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
//                        .withHorizontalRuleCreateDocument(true)
//                        .withIncludeCodeBlock(false)
//                        .withIncludeBlockquote(false)
//                        .withAdditionalMetadata("filename", filename)
//                        .withAdditionalMetadata("status", status)
//                        .build();
//                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
//                allDocuments.addAll(markdownDocumentReader.get());
//            }
//        } catch (IOException e) {
//           log.error("Markdown 文档加载失败", e);
//        }
//        return allDocuments;
//    }
//}
package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 写作智能体应用文档加载器
 *
 * 适配“结构化小说知识库 Markdown”：
 * 1. 文件顶部支持 YAML 风格 front matter
 * 2. 按 ## 一级主题、### 条目名 进行结构切分
 * 3. 条目标题下支持连续的 "- key: value" 元数据块
 * 4. 对超长条目再做 token 级二次切分
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {

    /**
     * Markdown 顶部 front matter
     */
    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("\\A---\\s*\\R(.*?)\\R---\\s*(?:\\R|$)", Pattern.DOTALL);

    /**
     * 二级标题：## 人物
     */
    private static final Pattern H2_PATTERN =
            Pattern.compile("(?m)^##\\s+(.+?)\\s*$");

    /**
     * 三级标题：### 萧炎
     */
    private static final Pattern H3_PATTERN =
            Pattern.compile("(?m)^###\\s+(.+?)\\s*$");

    /**
     * 元数据行：- key: value
     */
    private static final Pattern META_LINE_PATTERN =
            Pattern.compile("^\\s*-\\s*([a-zA-Z0-9_\\-]+)\\s*:\\s*(.*?)\\s*$");

    private final ResourcePatternResolver resourcePatternResolver;

    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇 Markdown 文档
     */
    public List<Document> loadMarkdowns() {
        List<Document> structuredDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");

            // 保证稳定顺序，便于排查
            List<Resource> sortedResources = new ArrayList<>(List.of(resources));
            sortedResources.sort(Comparator.comparing(resource ->
                    Optional.ofNullable(resource.getFilename()).orElse("")));

            for (Resource resource : sortedResources) {
                structuredDocuments.addAll(loadSingleMarkdown(resource));
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }

        // 二次切分：对超长结构化条目做细分
        List<Document> splitDocuments = splitOversizedDocuments(structuredDocuments);

        // 给每个 entry 下的 chunk 编号
        return addChunkOrderMetadata(splitDocuments);
    }

    /**
     * 读取单个 Markdown 文件，并按结构进行切分
     */
    private List<Document> loadSingleMarkdown(Resource resource) {
        String filename = Optional.ofNullable(resource.getFilename()).orElse("unknown.md");

        try {
            String rawMarkdown = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            FrontMatterResult frontMatterResult = extractFrontMatter(rawMarkdown);

            Map<String, Object> fileMetadata = new LinkedHashMap<>();
            fileMetadata.put("source_type", "novel_kb");
            fileMetadata.put("file_name", filename);
            fileMetadata.put("source", filename);
            fileMetadata.put("format", "markdown");
            fileMetadata.put("book_name", inferBookName(filename, frontMatterResult.frontMatter));
            fileMetadata.putAll(frontMatterResult.frontMatter);

            return splitByStructure(frontMatterResult.body, fileMetadata);
        } catch (IOException e) {
            log.error("读取 Markdown 文件失败: {}", filename, e);
            return List.of();
        }
    }

    /**
     * 第一段切分：按 ## / ### 结构切分
     */
    private List<Document> splitByStructure(String markdownBody, Map<String, Object> fileMetadata) {
        List<Document> documents = new ArrayList<>();

        List<HeadingBlock> h2Blocks = findHeadingBlocks(markdownBody, H2_PATTERN);

        // 没有二级标题，整个文件作为一个文档
        if (h2Blocks.isEmpty()) {
            String content = markdownBody == null ? "" : markdownBody.trim();
            if (!content.isBlank()) {
                Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata);
                metadata.put("section_type", "document");
                metadata.put("section_title", "全文");
                metadata.put("entry_type", "document");
                metadata.put("entry_title", Objects.toString(fileMetadata.get("book_name"), "全文"));
                metadata.put("entry_id", buildEntryId(
                        Objects.toString(fileMetadata.get("file_name"), "unknown.md"),
                        "document",
                        Objects.toString(fileMetadata.get("book_name"), "全文")
                ));
                metadata.put("chunking_strategy", "fallback_full_document");

                documents.add(new Document(content, metadata));
            }
            return documents;
        }

        int sectionOrder = 0;
        for (HeadingBlock h2Block : h2Blocks) {
            sectionOrder++;

            String sectionTitle = cleanHeading(h2Block.title);
            String sectionType = normalizeSectionType(sectionTitle);
            String sectionBody = h2Block.body == null ? "" : h2Block.body.trim();

            if (sectionBody.isBlank()) {
                continue;
            }

            List<HeadingBlock> h3Blocks = findHeadingBlocks(sectionBody, H3_PATTERN);

            // 某些主题下可能没有三级标题，比如整个“世界观”就是一大块说明
            if (h3Blocks.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata);
                metadata.put("section_type", sectionType);
                metadata.put("section_title", sectionTitle);
                metadata.put("section_order", sectionOrder);
                metadata.put("entry_type", inferEntryType(sectionType, sectionTitle, null));
                metadata.put("entry_title", sectionTitle);
                metadata.put("entry_order", 1);
                metadata.put("entry_id", buildEntryId(
                        Objects.toString(fileMetadata.get("file_name"), "unknown.md"),
                        sectionType,
                        sectionTitle
                ));
                metadata.put("chunking_strategy", "h2_only");

                String cleanedContent = cleanContent(sectionBody);
                if (!cleanedContent.isBlank()) {
                    documents.add(new Document(cleanedContent, metadata));
                }
                continue;
            }

            int entryOrder = 0;
            for (HeadingBlock h3Block : h3Blocks) {
                entryOrder++;

                String entryTitle = cleanHeading(h3Block.title);
                EntryContent entryContent = parseEntryContent(h3Block.body);

                Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata);
                metadata.put("section_type", sectionType);
                metadata.put("section_title", sectionTitle);
                metadata.put("section_order", sectionOrder);
                metadata.put("entry_type", inferEntryType(sectionType, sectionTitle, entryContent.metadata));
                metadata.put("entry_title", entryTitle);
                metadata.put("entry_order", entryOrder);
                metadata.put("entry_id", buildEntryId(
                        Objects.toString(fileMetadata.get("file_name"), "unknown.md"),
                        sectionType,
                        entryTitle
                ));
                metadata.put("chunking_strategy", "h2_h3_structured");
                metadata.putAll(entryContent.metadata);

                String cleanedContent = cleanContent(entryContent.content);
                if (!cleanedContent.isBlank()) {
                    documents.add(new Document(cleanedContent, metadata));
                }
            }
        }

        return documents;
    }

    /**
     * 第二段切分：对超长条目做 token 级细分
     *
     * 兼容你当前 Spring AI 版本：
     * 只使用 5 参数构造器，不传 punctuationMarks
     */
    private List<Document> splitOversizedDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return documents;
        }

        TokenTextSplitter splitter = new TokenTextSplitter(
                700,
                300,
                80,
                20000,
                true
        );

        return splitter.apply(documents);
    }

    /**
     * 给每个条目下的 chunk 增加顺序元数据
     */
    private List<Document> addChunkOrderMetadata(List<Document> documents) {
        Map<String, List<Document>> grouped = new LinkedHashMap<>();

        for (Document document : documents) {
            String entryId = Objects.toString(document.getMetadata().get("entry_id"), document.getId());
            grouped.computeIfAbsent(entryId, key -> new ArrayList<>()).add(document);
        }

        List<Document> result = new ArrayList<>();
        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            List<Document> sameEntryDocuments = entry.getValue();
            int total = sameEntryDocuments.size();

            for (int i = 0; i < sameEntryDocuments.size(); i++) {
                Document doc = sameEntryDocuments.get(i);
                Map<String, Object> metadata = new LinkedHashMap<>(doc.getMetadata());
                metadata.put("chunk_index", i + 1);
                metadata.put("chunk_total", total);

                String text = Objects.toString(doc.getText(), "");
                String id = doc.getId();

                if (id == null || id.isBlank()) {
                    result.add(new Document(text, metadata));
                } else {
                    result.add(new Document(id, text, metadata));
                }
            }
        }

        return result;
    }

    /**
     * 提取 front matter
     */
    private FrontMatterResult extractFrontMatter(String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.isBlank()) {
            return new FrontMatterResult(new LinkedHashMap<>(), "");
        }

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(rawMarkdown);
        if (!matcher.find()) {
            return new FrontMatterResult(new LinkedHashMap<>(), rawMarkdown);
        }

        String frontMatterText = matcher.group(1);
        String body = rawMarkdown.substring(matcher.end());

        Map<String, Object> frontMatter = parseKeyValueBlock(frontMatterText);
        return new FrontMatterResult(frontMatter, body);
    }

    /**
     * 查找指定标题层级的块
     */
    private List<HeadingBlock> findHeadingBlocks(String text, Pattern headingPattern) {
        List<HeadingBlock> blocks = new ArrayList<>();
        Matcher matcher = headingPattern.matcher(text);

        List<HeadingMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new HeadingMatch(matcher.start(), matcher.end(), matcher.group(1)));
        }

        for (int i = 0; i < matches.size(); i++) {
            HeadingMatch current = matches.get(i);
            int bodyStart = current.end;
            int bodyEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : text.length();
            String body = text.substring(bodyStart, bodyEnd);
            blocks.add(new HeadingBlock(current.title, body));
        }

        return blocks;
    }

    /**
     * 解析三级标题下的元数据和正文
     *
     * 约定格式：
     * ### 萧炎
     * - entry_type: character
     * - aliases: 岩枭|炎帝
     * - factions: 萧家|星陨阁
     *
     * 正文...
     */
    private EntryContent parseEntryContent(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new EntryContent(new LinkedHashMap<>(), "");
        }

        String[] lines = rawBody.split("\\R", -1);
        Map<String, Object> metadata = new LinkedHashMap<>();

        int index = 0;

        // 跳过开头空行
        while (index < lines.length && lines[index].isBlank()) {
            index++;
        }

        // 连续的 - key: value 视为 metadata block
        while (index < lines.length) {
            Matcher matcher = META_LINE_PATTERN.matcher(lines[index]);
            if (!matcher.matches()) {
                break;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (!key.isBlank() && !value.isBlank()) {
                metadata.put(key, value);
            }
            index++;
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = index; i < lines.length; i++) {
            contentBuilder.append(lines[i]);
            if (i < lines.length - 1) {
                contentBuilder.append('\n');
            }
        }

        return new EntryContent(metadata, contentBuilder.toString().trim());
    }

    /**
     * 解析 front matter 的 key: value
     */
    private Map<String, Object> parseKeyValueBlock(String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }

        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }

            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0 || colonIndex >= trimmed.length() - 1) {
                continue;
            }

            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();

            if (!key.isBlank() && !value.isBlank()) {
                map.put(key, stripQuote(value));
            }
        }
        return map;
    }

    /**
     * 根据 section 推断 entry_type
     */
    private String inferEntryType(String sectionType, String sectionTitle, Map<String, Object> metadata) {
        if (metadata != null) {
            Object explicit = metadata.get("entry_type");
            if (explicit != null && !Objects.toString(explicit).isBlank()) {
                return Objects.toString(explicit);
            }
        }

        return switch (sectionType) {
            case "worldview" -> "world_rule";
            case "character" -> "character";
            case "relationship" -> "relationship";
            case "location" -> "location";
            case "faction" -> "faction";
            case "power_system" -> "power_system";
            case "plot" -> "plot_event";
            case "timeline" -> "timeline_event";
            case "artifact" -> "artifact_or_technique";
            case "mystery" -> "mystery";
            case "terminology" -> "terminology";
            default -> {
                if (sectionTitle.contains("人物")) {
                    yield "character";
                }
                if (sectionTitle.contains("地点")) {
                    yield "location";
                }
                if (sectionTitle.contains("势力")) {
                    yield "faction";
                }
                yield "topic";
            }
        };
    }

    /**
     * 规范化 section type，便于过滤检索
     */
    private String normalizeSectionType(String sectionTitle) {
        String title = sectionTitle == null ? "" : sectionTitle.replace(" ", "");

        if (title.contains("世界观") || title.contains("设定") || title.contains("规则")) {
            return "worldview";
        }
        if (title.contains("人物关系") || title.contains("关系")) {
            return "relationship";
        }
        if (title.contains("人物") || title.contains("角色")) {
            return "character";
        }
        if (title.contains("地点") || title.contains("地域") || title.contains("场景")) {
            return "location";
        }
        if (title.contains("势力") || title.contains("组织") || title.contains("宗门") || title.contains("阵营")) {
            return "faction";
        }
        if (title.contains("境界") || title.contains("战力") || title.contains("修炼") || title.contains("体系")) {
            return "power_system";
        }
        if (title.contains("情节") || title.contains("剧情") || title.contains("事件") || title.contains("主线")) {
            return "plot";
        }
        if (title.contains("时间线")) {
            return "timeline";
        }
        if (title.contains("功法") || title.contains("法宝") || title.contains("血脉") || title.contains("宝物")) {
            return "artifact";
        }
        if (title.contains("谜团") || title.contains("伏笔") || title.contains("反转")) {
            return "mystery";
        }
        if (title.contains("名词") || title.contains("术语") || title.contains("别名")) {
            return "terminology";
        }
        return "topic";
    }

    /**
     * 推断书名
     */
    private String inferBookName(String filename, Map<String, Object> frontMatter) {
        Object frontMatterBookName = frontMatter.get("book_name");
        if (frontMatterBookName != null && !Objects.toString(frontMatterBookName).isBlank()) {
            return Objects.toString(frontMatterBookName);
        }

        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    /**
     * entry_id：稳定即可，不要求可读
     */
    private String buildEntryId(String filename, String sectionType, String entryTitle) {
        String raw = filename + "::" + sectionType + "::" + entryTitle;
        return "entry_" + Integer.toHexString(raw.hashCode());
    }

    private String cleanHeading(String heading) {
        if (heading == null) {
            return "";
        }
        return heading
                .replaceAll("\\s+", " ")
                .replaceAll("[#*`]+", "")
                .trim();
    }

    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?m)^\\s+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String stripQuote(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static class FrontMatterResult {
        private final Map<String, Object> frontMatter;
        private final String body;

        private FrontMatterResult(Map<String, Object> frontMatter, String body) {
            this.frontMatter = frontMatter;
            this.body = body;
        }
    }

    private static class HeadingMatch {
        private final int start;
        private final int end;
        private final String title;

        private HeadingMatch(int start, int end, String title) {
            this.start = start;
            this.end = end;
            this.title = title;
        }
    }

    private static class HeadingBlock {
        private final String title;
        private final String body;

        private HeadingBlock(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    private static class EntryContent {
        private final Map<String, Object> metadata;
        private final String content;

        private EntryContent(Map<String, Object> metadata, String content) {
            this.metadata = metadata;
            this.content = content;
        }
    }
}