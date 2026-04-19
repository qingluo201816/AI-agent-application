package com.qingluo.writeaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
@Slf4j
public class WriteAppVectorStoreConfig {

    @Resource
    private WriteAppDocumentLoader writeAppDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Value("${app.rag.local-store-path:tmp/vector-store/write-app.json}")
    private String localStorePath;

    @Bean
    VectorStore writeAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        Path storePath = Path.of(localStorePath).toAbsolutePath().normalize();
        File storeFile = storePath.toFile();

        if (storeFile.exists() && storeFile.length() > 0) {
            try {
                simpleVectorStore.load(storeFile);
                log.info("Loaded local vector store from {}", storePath);
                return simpleVectorStore;
            } catch (RuntimeException e) {
                log.warn("Failed to load local vector store from {}, rebuilding it", storePath, e);
            }
        }

        List<Document> documentList = writeAppDocumentLoader.loadMarkdowns();
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
        simpleVectorStore.add(enrichedDocuments);
        saveVectorStore(simpleVectorStore, storePath);
        return simpleVectorStore;
    }

    private void saveVectorStore(SimpleVectorStore simpleVectorStore, Path storePath) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            simpleVectorStore.save(storePath.toFile());
            log.info("Saved local vector store to {}", storePath);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to persist local vector store to {}", storePath, e);
        }
    }
}
