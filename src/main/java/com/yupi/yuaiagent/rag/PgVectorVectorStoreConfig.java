package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class PgVectorVectorStoreConfig {

    /**
     * DashScope 单次 embedding 最多 25 条文本
     */
    private static final int EMBEDDING_BATCH_SIZE = 25;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(25)
                .build();
        //public.vector_store 表
          // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        if (documents == null || documents.isEmpty()) {
            return vectorStore;
        }

        // 分批写入，避免 DashScope 单次输入文本超过 25 条
        batchAddDocuments(vectorStore, documents, EMBEDDING_BATCH_SIZE);

        return vectorStore;
    }

    /**
     * 分批添加文档到向量库
     */
    private void batchAddDocuments(VectorStore vectorStore, List<Document> documents, int batchSize) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须大于 0");
        }

        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            List<Document> batch = documents.subList(start, end);

            // 某些实现对 subList 比较敏感，拷贝一份更稳
            vectorStore.add(Collections.unmodifiableList(batch));
        }
    }
}