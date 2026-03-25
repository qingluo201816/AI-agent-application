package com.qingluo.writeaiagent.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public enum NovelSessionMode {

    STATE_MEMORY("state_memory", "State Memory", List.of("state_memory", "novel-state-memory")),
    INSPIRATION_ASSIST("inspiration_assist", "Inspiration Assist", List.of("inspiration_assist", "novel-inspiration-assist")),
    KEYWORD_CONTINUATION("keyword_continuation", "Keyword Continuation", List.of("keyword_continuation", "novel-keyword-continuation"));

    private final String key;
    private final String label;
    private final List<String> chatIdPrefixes;

    NovelSessionMode(String key, String label, List<String> chatIdPrefixes) {
        this.key = key;
        this.label = label;
        this.chatIdPrefixes = chatIdPrefixes;
    }

    @JsonValue
    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String createChatId() {
        long randomValue = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String suffix = Long.toString(randomValue, 36).toLowerCase(Locale.ROOT);
        if (suffix.length() < 8) {
            suffix = "0".repeat(8 - suffix.length()) + suffix;
        }
        return key + "_" + suffix.substring(0, 8);
    }

    public boolean matchesChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return false;
        }
        return chatIdPrefixes.stream().anyMatch(prefix ->
                chatId.equals(prefix)
                        || chatId.startsWith(prefix + "_")
                        || chatId.startsWith(prefix + "-")
        );
    }

    public static Optional<NovelSessionMode> fromChatId(String chatId) {
        return Arrays.stream(values())
                .filter(mode -> mode.matchesChatId(chatId))
                .findFirst();
    }

    @JsonCreator
    public static NovelSessionMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.key.equals(normalizedValue)
                        || mode.name().equalsIgnoreCase(normalizedValue)
                        || mode.chatIdPrefixes.contains(normalizedValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported novel session mode: " + value));
    }
}
