package com.qingluo.writeaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WriteManusTest {

    @Resource
    private WriteManus writeManus;

    @Test
    public void run() {
        String userPrompt = """
                我在写现代都市小说，请帮我调研上海静安区适合写进剧情的 5 个地点，
                并结合一些网络图片，为每个地点给出可用的场景描写要点，
                并以 PDF 格式输出“场景素材集”""";
        String answer = writeManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
