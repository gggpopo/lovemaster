package com.yupi.yuaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件持久化的对话记忆仓库（用于 Spring AI MessageWindowChatMemory）。
 * <p>
 * - 每个 conversationId 对应一个文件
 * - 使用 Kryo 序列化 Message 列表（与 FileBasedChatMemory 的实现保持一致）
 */
@Slf4j
public class FileChatMemoryRepository implements ChatMemoryRepository {

    private final File baseDir;
    private static final Kryo KRYO = new Kryo();

    static {
        KRYO.setRegistrationRequired(false);
        KRYO.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileChatMemoryRepository(String dir) {
        this.baseDir = new File(dir);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            log.warn("无法创建对话记忆目录：{}", baseDir.getAbsolutePath());
        }
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }
        List<Message> conversationMessages = findByConversationId(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            //noinspection unchecked
            return KRYO.readObject(input, ArrayList.class);
        } catch (Exception e) {
            log.warn("读取对话记忆失败，conversationId={} file={}", conversationId, file.getAbsolutePath(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        File file = getConversationFile(conversationId);
        if (file.exists() && !file.delete()) {
            log.warn("删除对话记忆文件失败：{}", file.getAbsolutePath());
        }
    }

    @Override
    public List<String> findConversationIds() {
        File[] files = baseDir.listFiles((dir, name) -> name != null && name.endsWith(".kryo"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        return Arrays.stream(files)
                .map(File::getName)
                .map(name -> name.endsWith(".kryo") ? name.substring(0, name.length() - 5) : name)
                .toList();
    }

    // 兼容旧命名（便于项目内其他地方复用）
    public void add(String conversationId, List<Message> messages) {
        saveAll(conversationId, messages);
    }

    public List<Message> get(String conversationId) {
        return findByConversationId(conversationId);
    }

    public void clear(String conversationId) {
        deleteByConversationId(conversationId);
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            KRYO.writeObject(output, messages);
        } catch (IOException e) {
            log.warn("保存对话记忆失败，conversationId={} file={}", conversationId, file.getAbsolutePath(), e);
        }
    }

    private File getConversationFile(String conversationId) {
        // 避免文件名包含特殊字符；同时控制长度
        String safeName = sha256Hex(conversationId);
        return new File(baseDir, safeName + ".kryo");
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // 理论不会发生；退化为 hashCode
            return Integer.toHexString(input.hashCode());
        }
    }
}
