package com.yupi.yuaiagent.chatmemory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 把本地文件层与 Redis 层内容格式化成可读快照（用于测试日志输出）。
 */
final class MemoryStorageSnapshotFormatter {

    private MemoryStorageSnapshotFormatter() {
    }

    static String toMarkdown(String conversationId,
                             Path localWindowDir,
                             List<Path> localFiles,
                             List<Message> windowMessages,
                             String redisKey,
                             String redisSummary,
                             Long redisTtlSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Local + Redis Memory Snapshot\n\n");
        sb.append("- conversationId: `").append(escape(conversationId)).append("`\n");
        sb.append("- localWindowDir: `").append(escape(localWindowDir == null ? "" : localWindowDir.toString())).append("`\n");
        sb.append("- localFileCount: ").append(localFiles == null ? 0 : localFiles.size()).append("\n");
        sb.append("- redisKey: `").append(escape(redisKey)).append("`\n");
        sb.append("- redisTTLSeconds: ").append(redisTtlSeconds == null ? -1 : redisTtlSeconds).append("\n\n");

        sb.append("| Layer | Location | Data Preview |\n");
        sb.append("|---|---|---|\n");
        sb.append("| Local File Window | `")
                .append(escape(localWindowDir == null ? "" : localWindowDir.toString()))
                .append("` | ")
                .append(escape(shorten(formatWindowMessages(windowMessages), 220)))
                .append(" |\n");
        sb.append("| Redis Summary | `")
                .append(escape(redisKey))
                .append("` | ")
                .append(escape(shorten(redisSummary, 220)))
                .append(" |\n");

        if (localFiles != null && !localFiles.isEmpty()) {
            sb.append("\nLocal files:\n");
            for (Path file : localFiles) {
                long size = -1L;
                try {
                    size = Files.size(file);
                } catch (Exception ignored) {
                }
                sb.append("- `").append(escape(file.toString())).append("` (size=").append(size).append(" bytes)\n");
            }
        }
        return sb.toString();
    }

    private static String formatWindowMessages(List<Message> windowMessages) {
        if (windowMessages == null || windowMessages.isEmpty()) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < windowMessages.size(); i++) {
            Message message = windowMessages.get(i);
            if (message == null) {
                continue;
            }
            String type = message.getMessageType() == null ? "UNKNOWN" : message.getMessageType().name();
            String text = message.getText();
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(type).append(": ").append(shorten(text, 80));
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String compact = s.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "...";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|");
    }
}
