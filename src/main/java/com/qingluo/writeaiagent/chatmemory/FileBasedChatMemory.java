package com.qingluo.writeaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Persists chat history as one file per conversation id.
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    private static final String FILE_SUFFIX = ".kryo";
    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    private final Path baseDir;
    private final int maxMessages;
    private final ConcurrentMap<String, ReentrantLock> conversationLocks = new ConcurrentHashMap<>();

    public FileBasedChatMemory(String dir, int maxMessages) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
        this.maxMessages = maxMessages;
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create chat memory directory: " + this.baseDir, e);
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (isInvalidConversation(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        withConversationLock(conversationId, () -> {
            List<Message> conversationMessages = readConversation(conversationId);
            conversationMessages.addAll(messages);
            writeConversation(conversationId, trimMessages(conversationMessages));
        });
    }

    @Override
    public List<Message> get(String conversationId) {
        if (isInvalidConversation(conversationId)) {
            return new ArrayList<>();
        }
        return withConversationLock(conversationId, () -> new ArrayList<>(readConversation(conversationId)));
    }

    @Override
    public void clear(String conversationId) {
        if (isInvalidConversation(conversationId)) {
            return;
        }
        withConversationLock(conversationId, () -> {
            try {
                Files.deleteIfExists(getConversationFile(conversationId));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to clear chat memory for " + conversationId, e);
            } finally {
                conversationLocks.remove(conversationId);
            }
        });
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public Path getConversationFilePath(String conversationId) {
        return getConversationFile(conversationId);
    }

    public List<String> listConversationIds() {
        try (Stream<Path> paths = Files.list(baseDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(fileName -> fileName.endsWith(FILE_SUFFIX))
                    .map(fileName -> fileName.substring(0, fileName.length() - FILE_SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list chat memory files in " + baseDir, e);
        }
    }

    private List<Message> trimMessages(List<Message> messages) {
        if (messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
    }

    private List<Message> readConversation(String conversationId) {
        Path file = getConversationFile(conversationId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (InputStream fileInputStream = Files.newInputStream(file); Input input = new Input(fileInputStream)) {
            return KRYO.get().readObject(input, ArrayList.class);
        } catch (Exception e) {
            quarantineCorruptedFile(file);
            log.warn("Failed to read chat memory file {}, fallback to empty conversation", file, e);
            return new ArrayList<>();
        }
    }

    private void writeConversation(String conversationId, List<Message> messages) {
        Path file = getConversationFile(conversationId);
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream fileOutputStream = Files.newOutputStream(tempFile); Output output = new Output(fileOutputStream)) {
            KRYO.get().writeObject(output, messages);
            output.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write temp chat memory file for " + conversationId, e);
        }

        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            moveWithoutAtomic(file, tempFile, conversationId, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist chat memory for " + conversationId, e);
        }
    }

    private void moveWithoutAtomic(Path file, Path tempFile, String conversationId, IOException sourceException) {
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist chat memory for " + conversationId, sourceException);
        }
    }

    private void quarantineCorruptedFile(Path file) {
        Path backupFile = file.resolveSibling(file.getFileName() + ".corrupted");
        try {
            Files.move(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupException) {
            log.warn("Failed to backup corrupted chat memory file {}", file, backupException);
        }
    }

    private Path getConversationFile(String conversationId) {
        return baseDir.resolve(sanitizeConversationId(conversationId) + FILE_SUFFIX);
    }

    private String sanitizeConversationId(String conversationId) {
        return conversationId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isInvalidConversation(String conversationId) {
        return conversationId == null || conversationId.isBlank();
    }

    private void withConversationLock(String conversationId, Runnable task) {
        ReentrantLock lock = conversationLocks.computeIfAbsent(conversationId, key -> new ReentrantLock());
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    private <T> T withConversationLock(String conversationId, LockedSupplier<T> supplier) {
        ReentrantLock lock = conversationLocks.computeIfAbsent(conversationId, key -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface LockedSupplier<T> {
        T get();
    }
}
