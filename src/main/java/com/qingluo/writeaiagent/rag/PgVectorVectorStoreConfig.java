package com.qingluo.writeaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
@Profile("pgvector")
public class PgVectorVectorStoreConfig {

    private static final int EMBEDDING_BATCH_SIZE = 25;

    @Resource
    private WriteAppDocumentLoader writeAppDocumentLoader;

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

        List<Document> documents = writeAppDocumentLoader.loadMarkdowns();
        if (documents == null || documents.isEmpty()) {
            return vectorStore;
        }

        batchAddDocuments(vectorStore, documents, EMBEDDING_BATCH_SIZE);
        return vectorStore;
    }

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
            vectorStore.add(Collections.unmodifiableList(batch));
        }
    }
}
