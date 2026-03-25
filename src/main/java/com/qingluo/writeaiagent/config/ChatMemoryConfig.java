package com.qingluo.writeaiagent.config;

import com.qingluo.writeaiagent.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public FileBasedChatMemory fileBasedChatMemory(
            @Value("${app.chat-memory.dir:tmp/chat-memory}") String dir,
            @Value("${app.chat-memory.max-messages:20}") int maxMessages
    ) {
        return new FileBasedChatMemory(dir, maxMessages);
    }

    @Bean
    public ChatMemory chatMemory(FileBasedChatMemory fileBasedChatMemory) {
        return fileBasedChatMemory;
    }
}
