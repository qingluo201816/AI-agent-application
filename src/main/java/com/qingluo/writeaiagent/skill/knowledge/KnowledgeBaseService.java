package com.qingluo.writeaiagent.skill.knowledge;

import com.qingluo.writeaiagent.rag.WriteAppDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class KnowledgeBaseService {

    private static final String KNOWLEDGE_BASE_PATH = "src/main/resources/document/knowledge/";

    private final WriteAppDocumentLoader documentLoader;

    public KnowledgeBaseService(WriteAppDocumentLoader documentLoader) {
        this.documentLoader = documentLoader;
        ensureKnowledgeBaseDir();
    }

    private void ensureKnowledgeBaseDir() {
        try {
            Path path = Paths.get(KNOWLEDGE_BASE_PATH);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建知识库目录: {}", KNOWLEDGE_BASE_PATH);
            }
        } catch (IOException e) {
            log.error("创建知识库目录失败: {}", e.getMessage(), e);
        }
    }

    public String saveToKnowledgeBase(KnowledgeOrganizeResult result, String fileName) {
        if (result == null || result.getGeneratedMarkdown() == null) {
            log.warn("无法保存空结果到知识库");
            return null;
        }

        String finalFileName = fileName != null && !fileName.isBlank() ?
                sanitizeFileName(fileName) :
                "knowledge_" + UUID.randomUUID().toString().substring(0, 8) + ".md";

        if (!finalFileName.endsWith(".md")) {
            finalFileName += ".md";
        }

        String filePath = KNOWLEDGE_BASE_PATH + finalFileName;

        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(result.getGeneratedMarkdown());
            log.info("知识文档已保存: {}", filePath);
            return filePath;
        } catch (IOException e) {
            log.error("保存知识文档失败: {}, 错误: {}", filePath, e.getMessage(), e);
            return null;
        }
    }

    public List<Document> loadFromKnowledgeBase() {
        log.info("开始从知识库加载文档");
        List<Document> documents = documentLoader.loadMarkdowns();
        log.info("从知识库加载了 {} 个文档块", documents.size());
        return documents;
    }

    public List<Document> loadFromKnowledgeBase(String fileNamePattern) {
        log.info("从知识库加载文档，模式: {}", fileNamePattern);
        List<Document> allDocuments = documentLoader.loadMarkdowns();

        if (fileNamePattern == null || fileNamePattern.isBlank()) {
            return allDocuments;
        }

        return allDocuments.stream()
                .filter(doc -> {
                    String source = (String) doc.getMetadata().get("file_name");
                    return source != null && source.contains(fileNamePattern);
                })
                .toList();
    }

    public List<Document> loadBySectionType(String sectionType) {
        log.info("按 section_type 加载文档: {}", sectionType);
        List<Document> allDocuments = documentLoader.loadMarkdowns();

        return allDocuments.stream()
                .filter(doc -> sectionType.equals(doc.getMetadata().get("section_type")))
                .toList();
    }

    public List<Document> loadByEntryType(String entryType) {
        log.info("按 entry_type 加载文档: {}", entryType);
        List<Document> allDocuments = documentLoader.loadMarkdowns();

        return allDocuments.stream()
                .filter(doc -> entryType.equals(doc.getMetadata().get("entry_type")))
                .toList();
    }

    public List<Document> loadByMetadata(Map<String, Object> metadataFilter) {
        log.info("按元数据过滤加载文档: {}", metadataFilter);
        List<Document> allDocuments = documentLoader.loadMarkdowns();

        return allDocuments.stream()
                .filter(doc -> {
                    for (Map.Entry<String, Object> entry : metadataFilter.entrySet()) {
                        Object value = doc.getMetadata().get(entry.getKey());
                        if (value == null || !value.toString().equals(entry.getValue().toString())) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    public List<Document> loadCharacters(String bookName) {
        log.info("加载人物设定: {}", bookName);
        return loadByMetadata(Map.of(
                "section_type", "character",
                "book_name", bookName
        ));
    }

    public List<Document> loadRelationships(String bookName) {
        log.info("加载人物关系: {}", bookName);
        return loadByMetadata(Map.of(
                "section_type", "relationship",
                "book_name", bookName
        ));
    }

    public List<Document> loadPlotEvents(String bookName, String plotLine) {
        log.info("加载剧情事件: {}, {}", bookName, plotLine);
        if (plotLine != null && !plotLine.isBlank()) {
            return loadByMetadata(Map.of(
                    "section_type", "plot",
                    "book_name", bookName,
                    "plot_line", plotLine
            ));
        }
        return loadByMetadata(Map.of(
                "section_type", "plot",
                "book_name", bookName
        ));
    }

    public List<Document> loadTimeline(String bookName) {
        log.info("加载时间线: {}", bookName);
        return loadByMetadata(Map.of(
                "section_type", "timeline",
                "book_name", bookName
        ));
    }

    public boolean deleteKnowledgeFile(String fileName) {
        String filePath = KNOWLEDGE_BASE_PATH + sanitizeFileName(fileName);
        File file = new File(filePath);

        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("删除知识文件: {}", filePath);
            } else {
                log.warn("删除知识文件失败: {}", filePath);
            }
            return deleted;
        }

        log.warn("知识文件不存在: {}", filePath);
        return false;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unnamed";
        }
        return fileName.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}