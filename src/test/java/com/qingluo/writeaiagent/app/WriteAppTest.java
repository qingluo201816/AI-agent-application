package com.qingluo.writeaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class WriteAppTest {

    @Resource
    private WriteApp writeApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是小说作者晴落";
        String answer = writeApp.doChat(message, chatId);
        message = "我想写一个栗艺珊是笨蛋的情节，她25岁，刚大学毕业";
        answer = writeApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        message = "栗艺珊也办过一些聪明事，请续写一个";
        answer = writeApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我正在写玄幻小说，主角成长线总是断掉，帮我给出可执行优化建议";
        WriteApp.WritingReport writingReport = writeApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(writingReport);
    }

    @Test
    void doChatWithRagByStream() {
        String chatId = UUID.randomUUID().toString();
        String message = "主角是谁,描述一下他的故事";
        String answer = writeApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        testMessage("我在写都市小说，主角第一次见导师，帮我设计 3 个有戏剧张力的场景。");
        testMessage("我写的男女主冲突太平淡，看看编程导航网站（codefather.cn）有哪些冲突写作技巧可以借鉴？");
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");
        testMessage("执行 Python3 脚本来生成数据分析报告");
        testMessage("保存我的小说大纲为文件");
        testMessage("生成一份《玄幻小说第一卷大纲》PDF，包含主线、支线与角色成长线");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = writeApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        String message = "帮我搜索一些适合玄幻小说封面的参考图片";
        String answer = writeApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
