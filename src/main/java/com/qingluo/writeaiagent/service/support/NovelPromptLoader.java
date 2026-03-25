package com.qingluo.writeaiagent.service.support;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责加载并缓存小说模块提示词。
 */
@Component
public class NovelPromptLoader {

    private final ResourceLoader resourceLoader;

    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    public NovelPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String location) {
        return promptCache.computeIfAbsent(location, this::readPrompt);
    }

    private String readPrompt(String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载小说能力提示词失败: " + location, e);
        }
    }
}
