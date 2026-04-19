package com.qingluo.writeaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class PgVectorVectorStoreConfigTest {

    @Resource(name = "writeAppVectorStore")
    private VectorStore writeAppVectorStore;

    @Test
    void writeAppVectorStore() {
        List<Document> documents = List.of(
                new Document("学习编程最重要的是持续练习和建立反馈闭环", Map.of("meta1", "meta1")),
                new Document("codefather.cn 提供了很多编程和 AI 相关内容"),
                new Document("系统化拆解问题比盲目试错更有效", Map.of("meta2", "meta2")));
        writeAppVectorStore.add(documents);
        List<Document> results = writeAppVectorStore.similaritySearch(
                SearchRequest.builder().query("怎么学习编程").topK(3).build()
        );
        Assertions.assertNotNull(results);
    }
}