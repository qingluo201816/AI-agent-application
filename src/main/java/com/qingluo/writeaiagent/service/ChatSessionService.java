package com.qingluo.writeaiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.writeaiagent.chatmemory.FileBasedChatMemory;
import com.qingluo.writeaiagent.model.response.ChatSessionDetailResponse;
import com.qingluo.writeaiagent.model.response.ChatSessionMessageResponse;
import com.qingluo.writeaiagent.model.response.ChatSessionSummaryResponse;
import com.qingluo.writeaiagent.model.session.ChatSessionMetadata;
import com.qingluo.writeaiagent.model.session.NovelSessionMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class ChatSessionService {

    private static final String METADATA_DIR_NAME = "sessions";
    private static final String METADATA_SUFFIX = ".json";
    private static final int TITLE_MAX_LENGTH = 20;
    private static final int PREVIEW_MAX_LENGTH = 42;
    private static final Pattern REPLACEABLE_TITLE_PATTERN = Pattern.compile("^(新对话(\\s*\\d+)?)|(未命名会话)$");

    private final FileBasedChatMemory chatMemory;
    private final ObjectMapper objectMapper;
    private final Path metadataDir;
    private final Object metadataMonitor = new Object();

    public ChatSessionService(FileBasedChatMemory chatMemory, ObjectMapper objectMapper) {
        this.chatMemory = chatMemory;
        this.objectMapper = objectMapper;
        this.metadataDir = chatMemory.getBaseDir().resolve(METADATA_DIR_NAME);
        try {
            Files.createDirectories(this.metadataDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create session metadata directory: " + metadataDir, e);
        }
    }

    public List<ChatSessionSummaryResponse> listSessions(NovelSessionMode mode) {
        return loadAllMetadata().values().stream()
                .filter(metadata -> metadata.getMode() == mode)
                .sorted(byRecentUpdate())
                .map(this::toSummary)
                .toList();
    }

    public ChatSessionDetailResponse createSession(NovelSessionMode mode) {
        synchronized (metadataMonitor) {
            Map<String, ChatSessionMetadata> sessionIndex = loadAllMetadata();
            String chatId = nextChatId(sessionIndex.keySet(), mode);
            Instant now = Instant.now();
            ChatSessionMetadata metadata = new ChatSessionMetadata(
                    chatId,
                    mode,
                    nextDefaultTitle(sessionIndex.values(), mode),
                    null,
                    false,
                    now,
                    now,
                    ""
            );
            writeMetadata(metadata);
            return new ChatSessionDetailResponse(toSummary(metadata), List.of());
        }
    }

    public ChatSessionDetailResponse getSessionDetail(String chatId) {
        ChatSessionMetadata metadata = requireSession(chatId);
        List<ChatSessionMessageResponse> messages = getSessionMessages(chatId, metadata);
        return new ChatSessionDetailResponse(toSummary(metadata, messages), messages);
    }

    public ChatSessionSummaryResponse renameSession(String chatId, String newTitle) {
        synchronized (metadataMonitor) {
            ChatSessionMetadata metadata = requireSession(chatId);
            metadata.setTitle(normalizeManualTitle(newTitle));
            metadata.setUserRenamed(true);
            metadata.setUpdatedAt(Instant.now());
            writeMetadata(metadata);
            return toSummary(metadata);
        }
    }

    public void deleteSession(String chatId) {
        synchronized (metadataMonitor) {
            try {
                Files.deleteIfExists(getMetadataFile(chatId));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete session metadata for " + chatId, e);
            }
            chatMemory.clear(chatId);
        }
    }

    public String ensureSession(NovelSessionMode mode, String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return createSession(mode).session().chatId();
        }
        synchronized (metadataMonitor) {
            ChatSessionMetadata metadata = loadMetadata(chatId)
                    .orElseGet(() -> buildFreshMetadata(chatId, mode, Instant.now()));
            if (metadata.getMode() == null) {
                metadata.setMode(mode);
            }
            if (metadata.getCreatedAt() == null) {
                metadata.setCreatedAt(Instant.now());
            }
            metadata.setUpdatedAt(Instant.now());
            if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
                metadata.setTitle(nextDefaultTitle(loadAllMetadata().values(), mode));
            }
            writeMetadata(metadata);
        }
        return chatId;
    }

    public void recordUserMessage(String chatId, NovelSessionMode mode, String userMessage) {
        synchronized (metadataMonitor) {
            ChatSessionMetadata metadata = loadMetadata(chatId)
                    .orElseGet(() -> buildFreshMetadata(chatId, mode, Instant.now()));
            if (metadata.getMode() == null) {
                metadata.setMode(mode);
            }
            Instant now = Instant.now();
            if (metadata.getCreatedAt() == null) {
                metadata.setCreatedAt(now);
            }
            metadata.setUpdatedAt(now);
            metadata.setLastPreview(buildPreview(userMessage));
            if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
                metadata.setTitle(nextDefaultTitle(loadAllMetadata().values(), mode));
            }
            writeMetadata(metadata);
        }
    }

    public void recordAssistantReply(String chatId, NovelSessionMode mode, String userMessage, String assistantReply) {
        synchronized (metadataMonitor) {
            ChatSessionMetadata metadata = loadMetadata(chatId)
                    .orElseGet(() -> buildFreshMetadata(chatId, mode, Instant.now()));
            if (metadata.getMode() == null) {
                metadata.setMode(mode);
            }
            Instant now = Instant.now();
            if (metadata.getCreatedAt() == null) {
                metadata.setCreatedAt(now);
            }
            metadata.setUpdatedAt(now);
            metadata.setLastPreview(buildPreview(userMessage));

            if (!metadata.isUserRenamed()
                    && (metadata.getAutoGeneratedTitle() == null || metadata.getAutoGeneratedTitle().isBlank())
                    && isReplaceableTitle(metadata.getTitle(), chatId)) {
                String generatedTitle = generateAutoTitle(userMessage, assistantReply);
                if (generatedTitle != null && !generatedTitle.isBlank()) {
                    metadata.setAutoGeneratedTitle(generatedTitle);
                    metadata.setTitle(generatedTitle);
                }
            }
            writeMetadata(metadata);
        }
    }

    private Map<String, ChatSessionMetadata> loadAllMetadata() {
        synchronized (metadataMonitor) {
            Map<String, ChatSessionMetadata> sessions = new LinkedHashMap<>();
            try (Stream<Path> paths = Files.list(metadataDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(METADATA_SUFFIX))
                        .sorted()
                        .forEach(path -> readMetadata(path).ifPresent(metadata -> sessions.put(metadata.getChatId(), metadata)));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to scan session metadata", e);
            }
            reconcileLegacySessions(sessions);
            return sessions;
        }
    }

    private void reconcileLegacySessions(Map<String, ChatSessionMetadata> sessions) {
        for (String chatId : chatMemory.listConversationIds()) {
            if (sessions.containsKey(chatId)) {
                continue;
            }
            NovelSessionMode mode = NovelSessionMode.fromChatId(chatId).orElse(null);
            if (mode == null) {
                continue;
            }
            ChatSessionMetadata legacyMetadata = buildLegacyMetadata(chatId, mode);
            sessions.put(chatId, legacyMetadata);
            writeMetadata(legacyMetadata);
        }
    }

    private Optional<ChatSessionMetadata> readMetadata(Path path) {
        try {
            ChatSessionMetadata metadata = objectMapper.readValue(path.toFile(), ChatSessionMetadata.class);
            return Optional.of(normalizeMetadata(metadata));
        } catch (IOException e) {
            log.warn("Failed to read session metadata file {}", path, e);
            return Optional.empty();
        }
    }

    private Optional<ChatSessionMetadata> loadMetadata(String chatId) {
        Path metadataFile = getMetadataFile(chatId);
        if (Files.exists(metadataFile)) {
            return readMetadata(metadataFile);
        }
        Path conversationFile = chatMemory.getConversationFilePath(chatId);
        if (Files.exists(conversationFile)) {
            NovelSessionMode mode = NovelSessionMode.fromChatId(chatId).orElse(null);
            if (mode != null) {
                ChatSessionMetadata metadata = buildLegacyMetadata(chatId, mode);
                writeMetadata(metadata);
                return Optional.of(metadata);
            }
        }
        return Optional.empty();
    }

    private ChatSessionMetadata requireSession(String chatId) {
        return loadMetadata(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + chatId));
    }

    private ChatSessionMetadata normalizeMetadata(ChatSessionMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata.getMode() == null) {
            NovelSessionMode.fromChatId(metadata.getChatId()).ifPresent(metadata::setMode);
        }
        Instant now = Instant.now();
        if (metadata.getCreatedAt() == null) {
            metadata.setCreatedAt(now);
        }
        if (metadata.getUpdatedAt() == null) {
            metadata.setUpdatedAt(metadata.getCreatedAt());
        }
        if (metadata.getLastPreview() == null) {
            metadata.setLastPreview("");
        }
        if (metadata.getTitle() != null) {
            metadata.setTitle(metadata.getTitle().trim());
        }
        if (metadata.getAutoGeneratedTitle() != null) {
            metadata.setAutoGeneratedTitle(metadata.getAutoGeneratedTitle().trim());
        }
        return metadata;
    }

    private ChatSessionMetadata buildLegacyMetadata(String chatId, NovelSessionMode mode) {
        Path conversationFile = chatMemory.getConversationFilePath(chatId);
        Instant fileInstant = readFileInstant(conversationFile).orElse(Instant.now());
        return new ChatSessionMetadata(
                chatId,
                mode,
                "未命名会话",
                null,
                false,
                fileInstant,
                fileInstant,
                ""
        );
    }

    private ChatSessionMetadata buildFreshMetadata(String chatId, NovelSessionMode mode, Instant now) {
        return new ChatSessionMetadata(
                chatId,
                mode,
                "新对话",
                null,
                false,
                now,
                now,
                ""
        );
    }

    private Optional<Instant> readFileInstant(Path file) {
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            return Optional.of(lastModifiedTime.toInstant());
        } catch (IOException e) {
            log.warn("Failed to read file time for {}", file, e);
            return Optional.empty();
        }
    }

    private String nextChatId(Collection<String> existingChatIds, NovelSessionMode mode) {
        String chatId = mode.createChatId();
        while (existingChatIds.contains(chatId) || Files.exists(chatMemory.getConversationFilePath(chatId))) {
            chatId = mode.createChatId();
        }
        return chatId;
    }

    private String nextDefaultTitle(Collection<ChatSessionMetadata> metadataList, NovelSessionMode mode) {
        long nextIndex = metadataList.stream()
                .filter(metadata -> metadata.getMode() == mode)
                .count() + 1;
        return "新对话 " + nextIndex;
    }

    private List<ChatSessionMessageResponse> getSessionMessages(String chatId, ChatSessionMetadata metadata) {
        List<Message> storedMessages = chatMemory.get(chatId);
        List<ChatSessionMessageResponse> messages = new ArrayList<>();
        int index = 0;
        for (Message message : storedMessages) {
            if (message == null || message.getText() == null || message.getText().isBlank()) {
                continue;
            }
            String role = mapRole(message.getMessageType());
            if (role == null) {
                continue;
            }
            messages.add(new ChatSessionMessageResponse(
                    chatId + "_" + index,
                    role,
                    message.getText(),
                    extractTimestamp(message)
            ));
            index++;
        }
        return fillMissingTimestamps(messages, metadata);
    }

    private List<ChatSessionMessageResponse> fillMissingTimestamps(
            List<ChatSessionMessageResponse> messages,
            ChatSessionMetadata metadata
    ) {
        if (messages.isEmpty()) {
            return messages;
        }
        Instant start = metadata.getCreatedAt();
        Instant end = metadata.getUpdatedAt();
        if (start == null && end == null) {
            start = Instant.now().minusSeconds(messages.size());
            end = Instant.now();
        } else if (start == null) {
            start = end.minusSeconds(Math.max(messages.size() - 1L, 0L));
        } else if (end == null) {
            end = start.plusSeconds(Math.max(messages.size() - 1L, 0L));
        }
        long totalMillis = Math.max(Duration.between(start, end).toMillis(), 1000L);
        List<ChatSessionMessageResponse> resolvedMessages = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatSessionMessageResponse message = messages.get(i);
            Instant timestamp = message.timestamp();
            if (timestamp == null) {
                long offsetMillis = messages.size() == 1 ? 0L : (totalMillis * i) / Math.max(messages.size() - 1, 1);
                timestamp = start.plusMillis(offsetMillis);
            }
            resolvedMessages.add(new ChatSessionMessageResponse(
                    message.id(),
                    message.role(),
                    message.content(),
                    timestamp
            ));
        }
        return resolvedMessages;
    }

    private String mapRole(MessageType messageType) {
        if (messageType == null) {
            return null;
        }
        return switch (messageType) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            default -> null;
        };
    }

    private Instant extractTimestamp(Message message) {
        Map<String, Object> metadata = message.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<String> candidateKeys = List.of("timestamp", "createdAt", "time", "messageTime");
        for (String key : candidateKeys) {
            Instant parsed = parseInstant(metadata.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Instant parseInstant(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Instant instant) {
            return instant;
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (rawValue instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (rawValue instanceof String stringValue) {
            String value = stringValue.trim();
            if (value.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(value));
                } catch (NumberFormatException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }

    private ChatSessionSummaryResponse toSummary(ChatSessionMetadata metadata) {
        return toSummary(metadata, List.of());
    }

    private ChatSessionSummaryResponse toSummary(
            ChatSessionMetadata metadata,
            List<ChatSessionMessageResponse> messages
    ) {
        String preview = resolvePreview(metadata, messages);
        return new ChatSessionSummaryResponse(
                metadata.getChatId(),
                metadata.getMode(),
                resolveDisplayTitle(metadata),
                metadata.getAutoGeneratedTitle(),
                metadata.isUserRenamed(),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt(),
                preview
        );
    }

    private String resolveDisplayTitle(ChatSessionMetadata metadata) {
        if (metadata.getTitle() != null && !metadata.getTitle().isBlank()) {
            return metadata.getTitle();
        }
        if (metadata.getAutoGeneratedTitle() != null && !metadata.getAutoGeneratedTitle().isBlank()) {
            return metadata.getAutoGeneratedTitle();
        }
        return metadata.getChatId();
    }

    private String resolvePreview(ChatSessionMetadata metadata, List<ChatSessionMessageResponse> messages) {
        if (metadata.getLastPreview() != null && !metadata.getLastPreview().isBlank()) {
            return metadata.getLastPreview();
        }
        return messages.stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((first, second) -> second)
                .map(ChatSessionMessageResponse::content)
                .map(this::buildPreview)
                .orElse("");
    }

    private Comparator<ChatSessionMetadata> byRecentUpdate() {
        return Comparator
                .comparing(ChatSessionMetadata::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatSessionMetadata::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatSessionMetadata::getChatId);
    }

    private void writeMetadata(ChatSessionMetadata metadata) {
        try {
            Files.createDirectories(metadataDir);
            Path targetFile = getMetadataFile(metadata.getChatId());
            Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), metadata);
            moveMetadataFile(targetFile, tempFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist session metadata for " + metadata.getChatId(), e);
        }
    }

    private void moveMetadataFile(Path targetFile, Path tempFile) throws IOException {
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path getMetadataFile(String chatId) {
        return metadataDir.resolve(sanitizeChatId(chatId) + METADATA_SUFFIX);
    }

    private String sanitizeChatId(String chatId) {
        return chatId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String normalizeManualTitle(String title) {
        String normalized = normalizeTitleCandidate(title);
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title cannot be blank");
        }
        return normalized;
    }

    private boolean isReplaceableTitle(String title, String chatId) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.trim();
        return normalized.equals(chatId) || REPLACEABLE_TITLE_PATTERN.matcher(normalized).matches();
    }

    private String generateAutoTitle(String userMessage, String assistantReply) {
        List<String> candidates = new ArrayList<>();
        candidates.add(extractTitleCandidate(userMessage, true));
        candidates.add(extractTitleCandidate(assistantReply, false));
        for (String candidate : candidates) {
            String normalized = normalizeTitleCandidate(candidate);
            if (normalized != null && normalized.length() >= 4) {
                return normalized;
            }
        }
        return null;
    }

    private String extractTitleCandidate(String rawText, boolean userSide) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String normalized = rawText.replace("\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("(?i)关键词[:：]", "")
                .replaceAll("(?i)例如[:：]", "")
                .replaceAll("(?i)比如[:：]", "")
                .trim();
        String[] lines = normalized.split("[\\n。！？!?]");
        for (String line : lines) {
            String candidate = line.trim();
            if (candidate.isBlank()) {
                continue;
            }
            if (userSide) {
                candidate = candidate
                        .replaceAll("^请帮我", "")
                        .replaceAll("^帮我", "")
                        .replaceAll("^请", "")
                        .replaceAll("^我想", "")
                        .replaceAll("^我卡在", "")
                        .replaceAll("^现在", "")
                        .trim();
            } else {
                candidate = candidate
                        .replaceAll("^可以", "")
                        .replaceAll("^建议", "")
                        .replaceAll("^你可以", "")
                        .replaceAll("^这段", "")
                        .replaceAll("^这里", "")
                        .trim();
            }
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return normalized;
    }

    private String normalizeTitleCandidate(String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim()
                .replaceAll("[\"'“”‘’]", "")
                .replaceAll("[`~!@#$%^&*()+=<>?/\\\\|\\[\\]{}]", " ")
                .replaceAll("[，,；;：:。.!！？?、]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return null;
        }
        List<String> segments = Stream.of(normalized.split(" "))
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .filter(segment -> !isNoiseSegment(segment))
                .collect(Collectors.toList());
        if (!segments.isEmpty()) {
            normalized = String.join(" ", segments);
        }
        if (normalized.length() > TITLE_MAX_LENGTH) {
            normalized = normalized.substring(0, TITLE_MAX_LENGTH).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isNoiseSegment(String segment) {
        return List.of(
                "一下", "一下子", "内容", "剧情", "章节", "问题", "这个", "那个",
                "一下吧", "怎么", "继续", "设计", "思路", "方案", "建议"
        ).contains(segment);
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX_LENGTH).trim() + "...";
    }
}
