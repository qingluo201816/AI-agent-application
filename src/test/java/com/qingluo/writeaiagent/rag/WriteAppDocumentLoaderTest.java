package com.qingluo.writeaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WriteAppDocumentLoaderTest {

    @Resource
    private WriteAppDocumentLoader writeAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        writeAppDocumentLoader.loadMarkdowns();
    }
}
